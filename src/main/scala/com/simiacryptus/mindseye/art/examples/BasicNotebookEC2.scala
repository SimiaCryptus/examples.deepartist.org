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

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.lang.cudnn.CudaMemory
import com.simiacryptus.sparkbook.{AWSNotebookRunner, EC2Runner}

object BasicNotebookEC2 extends BasicNotebook with EC2Runner[Object] with AWSNotebookRunner[Object] {
  override val s3bucket: String = "symmetry.deepartist.org"

  override def className: String = "BasicNotebook"

  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.T2_L

  override def maxHeap: Option[String] = Option("4g")

  override def javaProperties: Map[String, String] = super.javaProperties ++ Map(
    "MAX_TOTAL_MEMORY" -> (4 * CudaMemory.GiB).toString,
    "MAX_DEVICE_MEMORY" -> (4 * CudaMemory.GiB).toString
  )
}
