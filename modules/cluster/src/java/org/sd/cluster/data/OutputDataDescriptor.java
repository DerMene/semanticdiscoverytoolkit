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
package org.sd.cluster.data;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;

/**
 * Class to encapsulate the definition of a data store for output.
 * <p>
 * @author Spence Koehler
 */
public abstract class OutputDataDescriptor extends DataDescriptor {
  
  private boolean append;

  protected OutputDataDescriptor() {
    super();

    this.append = false;
  }

  protected OutputDataDescriptor(String dataId, Collection<String> nodes,
                                 PartitionFunction partitionFunction, StringKeyFunction stringKeyFunction,
                                 String overridingDataPath, boolean append) {
    super(dataId, nodes, partitionFunction, stringKeyFunction, overridingDataPath);

    this.append = append;
  }

  /**
   * Open this descriptor's data for output.
   */
  public abstract void open() throws IOException;

  /**
   * Close this descriptor's data.
   */
  public abstract void close() throws IOException;

  protected boolean append() {
    return append;
  }

  /**
   * Write thie message to the dataOutput stream such that this message
   * can be completely reconstructed through this.read(dataInput).
   *
   * @param dataOutput  the data output to write to.
   */
  public void write(DataOutput dataOutput) throws IOException {
    super.write(dataOutput);
    dataOutput.writeBoolean(append);
  }

  /**
   * Read this message's contents from the dataInput stream that was written by
   * this.write(dataOutput).
   * <p>
   * NOTE: this requires all implementing classes to have a default constructor
   *       with no args.
   *
   * @param dataInput  the data output to write to.
   */
  public void read(DataInput dataInput) throws IOException {
    super.read(dataInput);
    append = dataInput.readBoolean();
  }
}
