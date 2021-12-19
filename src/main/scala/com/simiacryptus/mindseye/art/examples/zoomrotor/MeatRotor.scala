package com.simiacryptus.mindseye.art.examples.zoomrotor

import com.simiacryptus.mindseye.art.models.VGG19
import com.simiacryptus.mindseye.art.ops.{ContentMatcher, SingleChannelEnhancer}
import com.simiacryptus.mindseye.art.util.{BasicOptimizer, VisualNetwork, VisualStyleNetwork}
import com.simiacryptus.mindseye.lang.{Layer, Tensor}
import com.simiacryptus.mindseye.opt.region.TrustRegion
import com.simiacryptus.notebook.NotebookOutput

trait MeatRotor[U <: MeatRotor[U]] extends ZoomingRotor[U] {
  override val border: Double = 0.0
  override val magnification = Array(2.0)
  override val rotationalSegments = 6
  override val rotationalChannelPermutation: Array[Int] = Array(1, 2, 3)
  override val styles: Array[String] = Array(
    ""
  )
  override val keyframes = Array(
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/06880563-cbda-4ef3-ac52-62fe89a748f7/etc/image_4f139291550789d8.jpg",
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/06880563-cbda-4ef3-ac52-62fe89a748f7/etc/image_7fe7712270aece4c.jpg",
    "file:///H:/SimiaCryptus/all-projects/report/TextureTiledRotor/06880563-cbda-4ef3-ac52-62fe89a748f7/etc/image_7535f03cd247d629.jpg"
  )

  override val s3bucket: String = "test.deepartist.org"
  override val resolution: Int = 800
  override val totalZoom: Double = 0.01
  override val stepZoom: Double = 0.5
  override val innerCoeff = 0

  override def getOptimizer()(implicit log: NotebookOutput): BasicOptimizer = {
    log.eval(() => {
      new BasicOptimizer {
        override val trainingMinutes: Int = 90
        override val trainingIterations: Int = 10
        override val maxRate = 1e9

        override def trustRegion(layer: Layer): TrustRegion = null

        override def renderingNetwork(dims: Seq[Int]) = getKaleidoscope(dims.toArray).head
      }
    })
  }

  override def getStyle(innerMask: Tensor)(implicit log: NotebookOutput): VisualNetwork = {
    log.eval(() => {
      val outerMask = innerMask.map(x => 1 - x)
      var style: VisualNetwork = new VisualStyleNetwork(
        styleLayers = List(
          VGG19.VGG19_1c4
        ),
        styleModifiers = List(
          new SingleChannelEnhancer(11, 12)
        ).map(_.withMask(outerMask.addRef())),
        styleUrls = styles,
        magnification = magnification,
        viewLayer = dims => getKaleidoscope(dims.toArray)
      )
      if (innerCoeff > 0) style = style.asInstanceOf[VisualStyleNetwork].withContent(
        contentLayers = List(
          VGG19.VGG19_0a
        ), contentModifiers = List(
          new ContentMatcher().withMask(innerMask.addRef()).scale(innerCoeff)
        ))
      style
    })
  }

}