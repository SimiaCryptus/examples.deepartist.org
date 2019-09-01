/*
 * Copyright (c) 2019 by Andrew Charneski.
 *
 * The author licenses this file to you under the
 * Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.simiacryptus.mindseye.art.examples

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import com.simiacryptus.mindseye.art.models.VGG16
import com.simiacryptus.mindseye.art.ops._
import com.simiacryptus.mindseye.art.util.ArtSetup.{ec2client, s3client}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, _}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook.NotebookRunner
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner


object AnimatedRotor extends AnimatedRotor with LocalRunner[Object] with NotebookRunner[Object]

class AnimatedRotor extends RotorArt {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val minResolution = 300
  val maxResolution = 800
  val magnification = 2
  val steps = 3
  val keyframes = 3
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val rotationalSegments: Int = 3

  override def indexStr = "303"

  override def description = <div>
    Paints a series of images, each to match the content of one while in the style of another using:
    <ol>
      <li>Random noise initialization</li>
      <li>Standard VGG16 layers</li>
      <li>Operators to match content and constrain and enhance style</li>
      <li>Progressive resolution increase</li>
      <li>Rotational symmerty constraint caused by a kaliedoscopic image layer</li>
    </ol>
    The parameters for each frame are fixed, but due to the random initialization
    and loose constraints we can achive a dynamic effect.
  </div>.toString.trim

  override def inputTimeoutSeconds = 3600

  override def postConfigure(log: NotebookOutput) = log.eval { () =>
    () => {
      implicit val _ = log
      // First, basic configuration so we publish to our s3 site
      log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
      log.onComplete(() => upload(log): Unit)
      // Fetch input images (user upload prompts) and display rescaled copies
      log.p(log.jpg(ImageArtUtil.load(log, styleUrl, (maxResolution * Math.sqrt(magnification)).toInt), "Input Style"))
      log.p(log.jpg(ImageArtUtil.load(log, contentUrl, maxResolution), "Input Content"))
      def frames = keyframes * 2 - 1
      val canvases = (1 to frames).map(_ => new AtomicReference[Tensor](null)).toList
      // Execute the main process while registered with the site index
      val registration = registerWithIndexGIF_Cyclic(canvases.map(_.get()).map(t=>{
        val kaleidoscope = getKaleidoscope(t.getDimensions)
        val transformed = kaleidoscope.eval(t).getDataAndFree.getAndFree(0)
        kaleidoscope.freeRef()
        transformed
      }))
      try {
        paintEveryOther(
          contentUrl,
          initUrl,
          canvases,
          (1 to frames).map(f => f.toString -> {
            new VisualStyleNetwork(
              styleLayers = List(
                // We select all the lower-level layers to achieve a good balance between speed and accuracy.
                VGG16.VGG16_0,
                VGG16.VGG16_1a,
                VGG16.VGG16_1b1,
                VGG16.VGG16_1b2,
                VGG16.VGG16_1c1,
                VGG16.VGG16_1c2,
                VGG16.VGG16_1c3
              ),
              styleModifiers = List(
                // These two operators are a good combination for a vivid yet accurate style
                new GramMatrixEnhancer(),
                new MomentMatcher()
              ),
              styleUrl = List(styleUrl),
              magnification = magnification,
              // Our optimization network should be built with a kaliedoscope appended to the canvas
              viewLayer = dims => getKaleidoscope(dims.toArray)
            )
          }).toList,
          new BasicOptimizer {
            override val trainingMinutes: Int = 60
            override val trainingIterations: Int = 30
            override val maxRate = 1e9

            // The canvas result should be processed by the kaliedoscope
            override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).copyPipeline()

            // By default, we use a trust region that constrains the canvas pixel values from 0-256.
            // This conflicts with using a kaliedoscope, whose output is bounded and input may be out of that range.
            override def trustRegion(layer: Layer): TrustRegion = null
          },
          (dims: Seq[Int]) => getKaleidoscope(dims.toArray).copyPipeline(),
          new GeometricSequence {
            override val min: Double = minResolution
            override val max: Double = maxResolution
            override val steps = AnimatedRotor.this.steps
          }.toStream.map(_.round.toDouble): _*)
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }()
}