/*
 * Copyright (c) 2013, Scodec
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package scodec

import scodec.bits.BitVector

enum Log:
  case One(bits: BitVector, context: Option[String])
  case Append(l1: Log, l2: Log, context: Option[String])
  case Group(l1: Log, l2: Log, context: Option[String])
  case Nada

object Log:
  // case class One(bits: BitVector, context: Option[String])
  // case class More(bits: List[One], context: Option[String])

  def one(bits: BitVector, context: Option[String] = None): Log =
    One(bits, context)

  def none: Log = Log.Nada

extension (l: Log)
  def append(l2: Log): Log = Log.Append(l, l2, None)
  def group(l2: Log): Log = Log.Group(l, l2, None)
  def toList: List[(BitVector, Option[String])] = ???
  def withContext(context: String): Log =
    l match
      case Log.Nada              => Log.Nada
      case Log.One(b, _)         => Log.One(b, Some(context))
      case Log.Append(l1, l2, _) => Log.Append(l1, l2, Some(context))
      case Log.Group(l1, l2, _)  => Log.Group(l1, l2, Some(context))
