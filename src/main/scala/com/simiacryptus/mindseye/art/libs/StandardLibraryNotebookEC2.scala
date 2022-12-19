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

package com.simiacryptus.mindseye.art.libs

import com.simiacryptus.aws.exe.EC2NodeSettings
import com.simiacryptus.mindseye.art.libs.StandardLibraryNotebookLocal._main
import com.simiacryptus.notebook.Jsonable
import com.simiacryptus.sparkbook.aws.P2_XL


object StandardLibraryNotebookEC2 extends StandardLibraryNotebookEC2 {
  def main(args: Array[String]): Unit = _main(args)
}

class StandardLibraryNotebookEC2
  extends StandardLibraryNotebook[StandardLibraryNotebookEC2]
    with P2_XL[Object, StandardLibraryNotebookEC2]
    with Jsonable[StandardLibraryNotebookEC2] {
  override def nodeSettings: EC2NodeSettings = EC2NodeSettings.M5_XL_DL

  override def install: Boolean = super.install
}








