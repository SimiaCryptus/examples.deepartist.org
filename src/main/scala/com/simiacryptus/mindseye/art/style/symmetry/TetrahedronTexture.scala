package com.simiacryptus.mindseye.art.style.symmetry

import com.simiacryptus.mindseye.art.util.view.RotationalGroupView.TETRAHEDRON
import com.simiacryptus.sparkbook.aws.P2_XL

import scala.concurrent.duration.{FiniteDuration, _}


object TetrahedronTexture extends TetrahedronTexture
  with P2_XL
  //  with NotebookRunner[Object] with LocalRunner[Object]
{
  override val s3bucket: String = "symmetry.deepartist.org"
}

class TetrahedronTexture extends PolyhedralTexture {

  override def name: String = "Spherical Texture Map"
  override def indexStr = "202"
  def aspectRatio = 1
  val animationFrames = 32
  val optimizationViewLimit = 4
  override val rowsAndCols = 1
  override val count: Int = 1
  override def animationDelay: FiniteDuration = 250 milliseconds
  def group = TETRAHEDRON

  override def description = <div>
    Creates a texture map which can be wrapped around a sphere, by painting several rendered views of the sphere.
    This texture map is reduced in size by enforcing a 10-fold tetrahedral rotational symmetry.
  </div>.toString.trim

}

