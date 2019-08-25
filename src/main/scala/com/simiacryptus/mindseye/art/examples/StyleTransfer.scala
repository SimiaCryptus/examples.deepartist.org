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
import com.simiacryptus.mindseye.eval.Trainable
import com.simiacryptus.mindseye.lang.Tensor
import com.simiacryptus.mindseye.opt.Step
import com.simiacryptus.notebook.NotebookOutput
import com.simiacryptus.sparkbook._
import com.simiacryptus.sparkbook.util.Java8Util._
import com.simiacryptus.sparkbook.util.LocalRunner


object StyleTransfer extends StyleTransfer with LocalRunner[Object] with NotebookRunner[Object]

class StyleTransfer extends ArtSetup[Object] {

  val contentUrl = "upload:Content"
  val styleUrl = "upload:Style"
  val initUrl: String = "50 + noise * 0.5"
  val s3bucket: String = "examples.deepartist.org"
  val minResolution = 600
  val maxResolution = 1024
  val magnification = 2
  val steps = 3
  override def indexStr = "301"

  override def description =
    """
      |Paints an image to reproduce the content of one image using the style of another reference image.
      |""".stripMargin.trim

  override def inputTimeoutSeconds = 3600


  override def postConfigure(log: NotebookOutput) = log.eval { () => () => {
    implicit val _ = log
    log.setArchiveHome(URI.create(s"s3://$s3bucket/${getClass.getSimpleName.stripSuffix("$")}/${log.getId}/"))
    log.onComplete(() => upload(log): Unit)
    log.p(log.jpg(ImageArtUtil.load(log, styleUrl, (maxResolution * Math.sqrt(magnification)).toInt), "Input Style"))
    log.p(log.jpg(ImageArtUtil.load(log, contentUrl, maxResolution), "Input Content"))
    val canvas = new AtomicReference[Tensor](null)
    val registration = registerWithIndexJPG(canvas.get())
    NotebookRunner.withMonitoredJpg(() => canvas.get().toImage) {
      try {
        paint(contentUrl, initUrl, canvas, new VisualStyleContentNetwork(
          styleLayers = List(
            VGG16.VGG16_0,
            VGG16.VGG16_1a,
            VGG16.VGG16_1b1,
            VGG16.VGG16_1b2,
            VGG16.VGG16_1c1,
            VGG16.VGG16_1c2,
            VGG16.VGG16_1c3
          ),
          styleModifiers = List(
            new GramMatrixEnhancer(),
            new MomentMatcher()
          ),
          styleUrl = List(styleUrl),
          contentLayers = List(
            VGG16.VGG16_1b2
          ),
          contentModifiers = List(
            new ContentMatcher()
          ),
          magnification = magnification
        ), new BasicOptimizer {
          override val trainingMinutes: Int = 60
          override val trainingIterations: Int = 30
          override val maxRate = 1e9

          override def onStepComplete(trainable: Trainable, currentPoint: Step): Boolean = {
            super.onStepComplete(trainable, currentPoint)
          }
        }, new GeometricSequence {
          override val min: Double = minResolution
          override val max: Double = maxResolution
          override val steps = StyleTransfer.this.steps
        }.toStream.map(_.round.toDouble): _*)
        null
      } finally {
        registration.foreach(_.stop()(s3client, ec2client))
      }
    }
  }}()
}
