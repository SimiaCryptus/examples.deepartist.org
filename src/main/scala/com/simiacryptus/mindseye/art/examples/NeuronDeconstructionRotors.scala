/*
 * Copyright (c) 2020 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.art.examples

import java.awt.image.BufferedImage
import java.awt.{Font, Graphics2D}
import java.net.URI

import com.simiacryptus.mindseye.art.models.Inception5H
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.registry.JobRegistration
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.layers.java.AffineImgViewLayer
import com.simiacryptus.mindseye.util.ImageUtil
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.ref.wrappers.RefAtomicReference
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.NotebookRunner._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner

import scala.collection.mutable.ArrayBuffer


object NeuronDeconstructionRotors extends NeuronDeconstructionRotors with LocalRunner[Object] with NotebookRunner[Object]

class NeuronDeconstructionRotors extends RotorArt {
  type pipelineType = Inception5H
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  val sourceUrl: String = "upload:Source"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = ""
  val srcResolution = 1800
  val aspectRatio = 0.5774
  val repeat = 1
  val min_padding = 8
  val max_padding = 32
  val border_factor = 0.125

  override def indexStr = "202"

  override def description = <div>
    Creates a tiled and rotationally symmetric texture based on a style using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard Inception5H layers</li>
      <li>Operators constraining and enhancing style</li>
      <li>Progressive resolution increase</li>
      <li>Kaleidoscopic view layer in addition to tiling layer</li>
    </ol>
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = {
    log.eval[() => Unit](() => {
      () => {
        implicit val implicitLog = log
        // First, basic configuration so we publish to our s3 site
        if (Option(s3bucket).filter(!_.isEmpty).isDefined)
          log.setArchiveHome(URI.create(s"s3://$s3bucket/$className/${log.getId}/"))
        log.onComplete(() => upload(log): Unit)
        val srcImage = ImageArtUtil.loadImage(log, sourceUrl, srcResolution)
        log.p(log.jpg(srcImage, "Source"))
        val animationDelay = 1000
        val renderedCanvases = new ArrayBuffer[() => BufferedImage]
        // Execute the main process while registered with the site index
        val registration = registerWithIndexGIF(renderedCanvases.filter(_ != null).map(_ ()).toList, delay = animationDelay)
        try {
          withMonitoredGif(() => renderedCanvases.filter(_ != null).map(_ ()).toList, delay = animationDelay) {
            val allData: List[(pipelineType, Int, Double, Double)] = (for (
              res <- new GeometricSequence {
                override def min: Double = 256

                override def max: Double = srcResolution

                override def steps: Int = 10
              }.toStream;
              layer <- Inception5H.values()
            ) yield {
              val layerProduct: Tensor = simpleEval(layer.getPipeline.get(layer.name()), Tensor.fromRGB(ImageUtil.resize(srcImage, res.toInt, true)))
              (0 until layerProduct.getDimensions()(2)).map(band => (layer, band, res, layerProduct.selectBand(band).getDoubleStatistics().getAverage()))
            }).flatten.toList
            val adj = allData.groupBy(_._1).mapValues(_.map(_._4).toArray).mapValues(x => x.sum.toDouble / x.size)

            val bestPerResolution: Array[(pipelineType, Int, Double, Double)] = allData.groupBy(x => (x._1, x._2))
              .mapValues(_.maxBy(_._4)).values.toArray.sortBy(t => t._4 / adj(t._1)).reverse.take(20)
            for ((layer, rows) <- bestPerResolution.groupBy(_._1)) {
              log.subreport("Neurons in " + layer.name(), (sub: NotebookOutput) => {
                for ((_, band, res, _) <- rows) {
                  sub.h2(layer.name() + " " + band + " at " + res.floor.toInt)
                  sub.eval(() => {
                    allData.filter(_._1 == layer).filter(_._2 == band).map(t => t._3.toInt -> t._4).toMap
                  })
                  val size = renderedCanvases.size
                  (1 to repeat).map(_ => {
                    val image = test(layer, band, layer.name() + " " + band + " @ " + res.floor.toInt, 512)(sub)
                    if (renderedCanvases.size > size) {
                      renderedCanvases(size) = () => image
                    } else {
                      renderedCanvases += (() => image)
                    }
                  })
                }
              })
            }
          }
        } finally {
          registration.foreach(_.stop()(s3client, ec2client))
        }
      }
    })()
    null
  }

  def simpleEval(layer: Layer, tensor: Tensor) = {
    layer.eval(tensor).getData.get(0)
  }

  def test(layer: pipelineType, dimensionSelected: Int, imageLabel: String, resolutions: Int*)(log: NotebookOutput): BufferedImage = {
    val registration: Option[JobRegistration[Tensor]] = None
    try {
      val canvas = new RefAtomicReference[Tensor](null)

      def rotatedCanvas = {
        var input = canvas.get()
        if (null == input) input else {
          val viewLayer = getKaleidoscope(input.getDimensions).head
          val result = viewLayer.eval(input)
          viewLayer.freeRef()
          val data = result.getData
          result.freeRef()
          val tensor = data.get(0)
          data.freeRef()
          tensor
        }
      }

      // Kaleidoscope+Tiling layer used by the optimization engine.
      // Expands the canvas by a small amount, using tile wrap to draw in the expanded boundary.
      def viewLayer(dims: Seq[Int]) = {
        for(rotor <- getKaleidoscope(dims.toArray)) yield {
          val paddingX = Math.min(max_padding, Math.max(min_padding, dims(0) * border_factor)).toInt
          val paddingY = Math.min(max_padding, Math.max(min_padding, dims(1) * border_factor)).toInt
          val tiling = new AffineImgViewLayer(dims(0) + paddingX, dims(1) + paddingY, true)
          tiling.setOffsetX(-paddingX / 2)
          tiling.setOffsetY(-paddingY / 2)
          rotor.add(tiling).freeRef()
          rotor
        }
      }

      // Display a pre-tiled image inside the report itself
      withMonitoredJpg(() => Option(rotatedCanvas).map(tensor => {
        val image = tensor.toRgbImage
        tensor.freeRef()
        image
      }).orNull) {
        log.subreport("Painting", (sub: NotebookOutput) => {
          paint(
            contentUrl = initUrl,
            initUrl = initUrl,
            canvas = canvas.addRef(),
            network = new VisualStyleNetwork(
              styleLayers = List(
                layer
              ),
              styleModifiers = List(
                new SingleChannelEnhancer(dimensionSelected, dimensionSelected + 1)
              ),
              styleUrls = Seq(""),
              viewLayer = viewLayer
            )(log),
            optimizer = new BasicOptimizer {
              override val trainingMinutes: Int = 60
              override val trainingIterations: Int = 150
              override val maxRate = 1e9

              //override def trustRegion(layer: Layer): TrustRegion = null

              override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).head
            },
            aspect = Option(aspectRatio),
            resolutions = resolutions.toList.map(_.round.toDouble))(sub)
          null
        })
        uploadAsync(log)
      }(log)

      val image = rotatedCanvas.toImage
      if (null == image) image else {
        val graphics = image.getGraphics.asInstanceOf[Graphics2D]
        graphics.setFont(new Font("Calibri", Font.BOLD, 24))
        graphics.drawString(imageLabel, 10, 25)
        image
      }
    } finally {
      registration.foreach(_.stop()(s3client, ec2client))
    }
  }
}
