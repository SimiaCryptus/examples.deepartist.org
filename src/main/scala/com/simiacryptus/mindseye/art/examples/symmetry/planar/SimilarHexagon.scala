package com.simiacryptus.mindseye.art.examples.symmetry.planar

import com.simiacryptus.mindseye.art.examples.styled.SegmentStyleEC2
import com.simiacryptus.mindseye.art.examples.symmetry.SymmetricTexture
import com.simiacryptus.mindseye.art.util.view.{ImageView, RotatedVector}
import com.simiacryptus.mindseye.art.util.{GeometricSequence, Permutation}
import com.simiacryptus.notebook.{Jsonable, NotebookOutput}
import com.simiacryptus.sparkbook.aws.P2_XL

import java.net.URI
import java.util.UUID


object SimilarHexagonEC2 extends SimilarHexagonEC2; class SimilarHexagonEC2 extends SimilarHexagon[SimilarHexagonEC2] with P2_XL[Object, SimilarHexagonEC2] with Jsonable[SimilarHexagonEC2]
  //with NotebookRunner[Object] with LocalRunner[Object]
{


  override def name: String = SimilarHexagonEC2.super.name
}

class SimilarHexagon[T<:SimilarHexagon[T]] extends SymmetricTexture[T] {

  override def name: String = "6-way rotational similarity"

  override def indexStr = "202"

  def aspectRatio = 1

  override val rowsAndCols = 1

  override def description = <div>
    Creates a 6/8 hyperbolic tile with degenerate rotational symmetry.
    Produces self-similar repetition on 60-degree rotation.
  </div>.toString.trim

  def optimizerViews(implicit log: NotebookOutput) = {
    log.out("Symmetry Spec:")
    log.code(() => {
      Array(Array[ImageView](
        RotatedVector(rotation = List(1).map(_ * Math.PI * 2 / 6 -> Permutation.unity(3)).toMap)
      ))
    })
  }

  override def displayViews(implicit log: NotebookOutput): List[Array[ImageView]] = List(Array[ImageView](
    RotatedVector(rotation = List(1).map(_ * Math.PI * 2 / 6 -> Permutation.unity(3)).toMap),
  ))

  override def resolutions = new GeometricSequence {
    override val min: Double = 64
    override val max: Double = 1024
    override val steps = 5
  }.toStream.map(x => {
    x.round.toInt -> Array(6).map(Math.pow(_, 2)).flatMap(x => Array(x * 0.9, x))
  }: (Int, Seq[Double])).toList.sortBy(_._1)


}
