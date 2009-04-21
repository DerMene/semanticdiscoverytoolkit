/*
    Copyright 2009 Semantic Discovery, Inc. (www.semanticdiscovery.com)

    This file is part of the Semantic Discovery Toolkit.

    The Semantic Discovery Toolkit is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    The Semantic Discovery Toolkit is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with The Semantic Discovery Toolkit.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.sd.cio;


import org.sd.io.DataHelper;
import org.sd.io.Publishable;
import org.sd.util.ReflectUtil;
import org.sd.util.tree.Tree;
import org.sd.xml.XmlFactory;
import org.sd.xml.XmlLite;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * General utilities for helping with messages.
 * <p>
 * @author Spence Koehler
 */
public class MessageHelper extends DataHelper {
  
  public static void writePublishable(DataOutput dataOutput, Publishable publishable) throws IOException {
    if (publishable == null) {
      dataOutput.writeBoolean(false);
    }
    else {
      dataOutput.writeBoolean(true);
      final String classname = publishable.getClass().getName();
      writeString(dataOutput, classname);
      publishable.write(dataOutput);
    }
  }

  //todo: figure out how to typecast generics such that readPublishableCollection and writePublishableCollection would work.
//   public static List<Publishable> readPublishableList(DataInput dataInput) throws IOException {
//     List<Publishable> result = null;
//     final int numPublishables = dataInput.readInt();

//     if (numPublishables >= 0) {
//       result = new ArrayList<Publishable>();

//       for (int i = 0; i < numPublishables; ++i) {
//         result.add(readPublishable(dataInput));
//       }
//     }
//     return result;
//   }

//   public static void writePublishableList(DataOutput dataOutput, List<Publishable> publishableList) throws IOException {
//     if (publishableList == null) {
//       dataOutput.writeInt(-1);
//     }
//     else {
//       dataOutput.writeInt(publishableList.size());

//       for (Publishable publishable : publishableList) {
//         writePublishable(dataOutput, publishable);
//       }
//     }
//   }

  /**
   * Get the number of bytes needed to serialize this publishable beyond the
   * publishable's data itself.
   * <p>
   * The amount of overhead is the space for a boolean and the publishable's
   * class name string.
   */
  public static final int numOverheadBytes(Publishable publishable) {
    int result = 1;  // boolean

    if (publishable != null) {
      final String classname = publishable.getClass().getName();
      result += numOverheadBytes(classname);
    }

    return result;
  }

  public static Publishable readPublishable(DataInput dataInput) throws IOException {
    Publishable result = null;
    final boolean hasPublishable = dataInput.readBoolean();
    if (hasPublishable) {
      final String classname = readString(dataInput);
      result = (Publishable)ReflectUtil.newInstance(classname);
      result.read(dataInput);
    }
    return result;
  }

  /**
   * Write a non-publishable object that can be fully reconstructed through a
   * default no-argument constructor.
   */
  public static void writeNonPublishable(DataOutput dataOutput, Object object) throws IOException {
    if (object == null) {
      dataOutput.writeBoolean(false);
    }
    else {
      dataOutput.writeBoolean(true);
      final String classname = object.getClass().getName();
      writeString(dataOutput, classname);
    }
  }

  /**
   * Read a non-publishable object that can be fully reconstructed through a
   * default no-argument constructor.
   */
  public static Object readNonPublishable(DataInput dataInput) throws IOException {
    Object result = null;
    final boolean hasObject = dataInput.readBoolean();
    if (hasObject) {
      final String classname = readString(dataInput);
      result = ReflectUtil.newInstance(classname);
    }
    return result;
  }

  /**
   * Write an xml tree to the data output.
   */
  public static final void writeXmlTree(DataOutput dataOutput, Tree<XmlLite.Data> xmlTree, boolean htmlFlag) throws IOException {
    if (xmlTree == null) {
      dataOutput.writeBoolean(false);  // hasData=false
    }
    else {
      dataOutput.writeBoolean(true);   // hasData=true
      dataOutput.writeBoolean(htmlFlag);
      final String string = XmlLite.asXml(xmlTree, false);
      writeString(dataOutput, string);
    }
  }

  /**
   * Read an xml tree from the data input.
   */
  public static final Tree<XmlLite.Data> readXmlTree(DataInput dataInput) throws IOException {
    Tree<XmlLite.Data> result = null;
    final boolean hasData = dataInput.readBoolean();

    if (hasData) {
      final boolean htmlFlag = dataInput.readBoolean();
      final String string = readString(dataInput);
      result = XmlFactory.buildXmlTree(string, false, htmlFlag);
    }

    return result;
  }

  /**
   * Serialize a publishable into bytes.
   */
  public static final byte[] serializeXmlTree(Tree<XmlLite.Data> xmlTree, boolean htmlFlag) throws IOException {
    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    final DataOutputStream dataOut = new DataOutputStream(bytesOut);

    // serialize
    writeXmlTree(dataOut, xmlTree, htmlFlag);

    dataOut.close();
    final byte[] result = bytesOut.toByteArray();
    bytesOut.close();

    return result;
  }

  /**
   * Deserialize a publishable's bytes.
   */
  public static final Tree<XmlLite.Data> deserializeXmlTree(byte[] bytes) throws IOException {
    final ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
    final DataInputStream dataIn = new DataInputStream(bytesIn);

    // deserialize
    Tree<XmlLite.Data> result = readXmlTree(dataIn);

    dataIn.close();
    return result;
  }

  /**
   * Serialize a publishable into bytes.
   */
  public static final byte[] serialize(Publishable publishable) throws IOException {
    final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
    final DataOutputStream dataOut = new DataOutputStream(bytesOut);

    // serialize
    writePublishable(dataOut, publishable);

    dataOut.close();
    final byte[] result = bytesOut.toByteArray();
    bytesOut.close();

    return result;
  }

  /**
   * Deserialize a publishable's bytes.
   */
  public static final Publishable deserialize(byte[] bytes) throws IOException {
    final ByteArrayInputStream bytesIn = new ByteArrayInputStream(bytes);
    final DataInputStream dataIn = new DataInputStream(bytesIn);

    // deserialize
    Publishable result = readPublishable(dataIn);

    dataIn.close();
    return result;
  }
}
