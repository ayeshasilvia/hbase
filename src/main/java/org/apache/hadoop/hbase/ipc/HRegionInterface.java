/**
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.ipc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.HServerInfo;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.Restartable;
import org.apache.hadoop.hbase.Stoppable;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.MultiAction;
import org.apache.hadoop.hbase.client.MultiPut;
import org.apache.hadoop.hbase.client.MultiPutResponse;
import org.apache.hadoop.hbase.client.MultiResponse;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.RowMutations;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.hfile.histogram.HFileHistogram.Bucket;
import org.apache.hadoop.hbase.master.AssignmentPlan;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.io.MapWritable;

/**
 * Clients interact with HRegionServers using a handle to the HRegionInterface.
 *
 * <p>NOTE: if you change the interface, you must change the RPC version
 * number in HBaseRPCProtocolVersion
 */
public interface HRegionInterface extends HBaseRPCProtocolVersion, Restartable,
    Stoppable, ThriftClientInterface, IRegionScanService {

  /**
   * Calls an endpoint on an region server.
   *
   * TODO make regionName a list.
   *
   * @param epName      the endpoint name.
   * @param methodName  the method name.
   * @param regionName  the name of the region
   * @param startRow    the start row, inclusive
   * @param stopRow     the stop row, exclusive
   * @return  the computed value.
   */
  public byte[] callEndpoint(String epName, String methodName,
      ArrayList<byte[]> params, byte[] regionName, byte[] startRow,
      byte[] stopRow) throws IOException;

  /**
   * Get metainfo about an HRegion
   *
   * @param regionName name of the region
   * @return HRegionInfo object for region
   * @throws NotServingRegionException e
   */
  public HRegionInfo getRegionInfo(final byte [] regionName)
  throws NotServingRegionException;

  /**
   * Return all the data for the row that matches <i>row</i> exactly,
   * or the one that immediately preceeds it.
   *
   * @param regionName region name
   * @param row row key
   * @param family Column family to look for row in.
   * @return map of values
   * @throws IOException e
   */
  public Result getClosestRowBefore(final byte [] regionName,
    final byte [] row, final byte [] family)
  throws IOException;

  /**
   *
   * @return the regions served by this regionserver
   */
  public HRegion[] getOnlineRegionsAsArray();

  /**
   * Flush the given region
   */
  public void flushRegion(byte[] regionName)
    throws IllegalArgumentException, IOException;

  /**
   * Flush the given region if lastFlushTime < ifOlderThanTS
   */
  public void flushRegion(byte[] regionName, long ifOlderThanTS)
    throws IllegalArgumentException, IOException;

  /**
   * Gets last flush time (in milli sec) for the given region
   * @return the last flush time for a region
   */
  public long getLastFlushTime(byte[] regionName);

  /**
   * Gets last flush time (in milli sec) for all regions on the server
   * @return a map of regionName to the last flush time for the region
   */
  public MapWritable getLastFlushTimes();

  /**
   * Gets the current time (in milli sec) at the region server
   * @return time in milli seconds at the regionserver.
   */
  public long getCurrentTimeMillis();

  /**
   * Gets the current startCode at the region server
   * @return startCode -- time in milli seconds when the regionserver started.
   */
  public long getStartCode();

  /**
   * Get a list of store files for a particular CF in a particular region
   * @param region name
   * @param CF name
   * @return the list of store files
   */
  public List<String> getStoreFileList(byte[] regionName, byte[] columnFamily)
    throws IllegalArgumentException;

  /**
   * Get a list of store files for a set of CFs in a particular region
   * @param region name
   * @param CF names
   * @return the list of store files
   */
  public List<String> getStoreFileList(byte[] regionName,
      byte[][] columnFamilies) throws IllegalArgumentException;

  /**
   * Get a list of store files for all CFs in a particular region
   * @param region name
   * @return the list of store files
   */
  public List<String> getStoreFileList(byte[] regionName)
    throws IllegalArgumentException;

  /**
  * @param rollCurrentHLog if true, the current HLog is rolled and will be
  * included in the list returned
  * @return list of HLog files
  */
  public List<String> getHLogsList(boolean rollCurrentHLog)
      throws IOException;

  /**
   * TODO: deprecate this
   * Perform Get operation.
   * @param regionName name of region to get from
   * @param get Get operation
   * @return Result
   * @throws IOException e
   */
  public Result get(byte[] regionName, Get get) throws IOException;

  public Result[] get(byte[] regionName, List<Get> gets)
      throws IOException;

  /**
   * Perform exists operation.
   * @param regionName name of region to get from
   * @param get Get operation describing cell to test
   * @return true if exists
   * @throws IOException e
   */
  public boolean exists(byte [] regionName, Get get) throws IOException;

  /**
   * Put data into the specified region
   * @param regionName region name
   * @param put the data to be put
   * @throws IOException e
   */
  public void put(final byte [] regionName, final Put put)
  throws IOException;

  /**
   * Put an array of puts into the specified region
   *
   * @param regionName region name
   * @param puts List of puts to execute
   * @return The number of processed put's.  Returns -1 if all Puts
   * processed successfully.
   * @throws IOException e
   */
  public int put(final byte[] regionName, final List<Put> puts)
  throws IOException;

  /**
   * Deletes all the KeyValues that match those found in the Delete object,
   * if their ts <= to the Delete. In case of a delete with a specific ts it
   * only deletes that specific KeyValue.
   * @param regionName region name
   * @param delete delete object
   * @throws IOException e
   */
  public void delete(final byte[] regionName, final Delete delete)
  throws IOException;

  /**
   * Put an array of deletes into the specified region
   *
   * @param regionName region name
   * @param deletes delete List to execute
   * @return The number of processed deletes.  Returns -1 if all Deletes
   * processed successfully.
   * @throws IOException e
   */
  public int delete(final byte[] regionName, final List<Delete> deletes)
      throws IOException;

  /**
   * Atomically checks if a row/family/qualifier value match the expectedValue.
   * If it does, it adds the put. If passed expected value is null, then the
   * check is for non-existance of the row/column.
   *
   * @param regionName region name
   * @param row row to check
   * @param family column family
   * @param qualifier column qualifier
   * @param value the expected value
   * @param put data to put if check succeeds
   * @throws IOException e
   * @return true if the new put was execute, false otherwise
   */
  public boolean checkAndPut(final byte[] regionName, final byte[] row,
      final byte[] family, final byte[] qualifier, final byte[] value,
      final Put put) throws IOException;


  /**
   * Atomically checks if a row/family/qualifier value match the expectedValue.
   * If it does, it adds the delete. If passed expected value is null, then the
   * check is for non-existance of the row/column.
   *
   * @param regionName region name
   * @param row row to check
   * @param family column family
   * @param qualifier column qualifier
   * @param value the expected value
   * @param delete data to delete if check succeeds
   * @throws IOException e
   * @return true if the new delete was execute, false otherwise
   */
  public boolean checkAndDelete(final byte[] regionName, final byte[] row,
      final byte[] family, final byte[] qualifier, final byte[] value,
      final Delete delete) throws IOException;

  /**
   * Atomically increments a column value. If the column value isn't long-like,
   * this could throw an exception. If passed expected value is null, then the
   * check is for non-existance of the row/column.
   *
   * @param regionName region name
   * @param row row to check
   * @param family column family
   * @param qualifier column qualifier
   * @param amount long amount to increment
   * @param writeToWAL whether to write the increment to the WAL
   * @return new incremented column value
   * @throws IOException e
   */
  public long incrementColumnValue(byte[] regionName, byte[] row,
      byte[] family, byte[] qualifier, long amount, boolean writeToWAL)
      throws IOException;

  /**
   * Get a configuration property from an HRegion
   *
   * @param String propName name of configuration property
   * @return String value of property
   * @throws IOException e
   */
  public String getConfProperty(String paramName) throws IOException;

  //
  // remote scanner interface
  //

  /**
   * Opens a remote scanner with a RowFilter.
   *
   * @param regionName name of region to scan
   * @param scan configured scan object
   * @return scannerId scanner identifier used in other calls
   * @throws IOException e
   */
  public long openScanner(final byte[] regionName, final Scan scan)
      throws IOException;

  public void mutateRow(byte[] regionName, RowMutations arm)
      throws IOException;

  public void mutateRow(byte[] regionName, List<RowMutations> armList)
      throws IOException;
  /**
   * Get the next set of values. Do not use with thrift
   * @param scannerId clientId passed to openScanner
   * @return map of values; returns null if no results.
   * @throws IOException e
   */
  @Deprecated
  public Result next(long scannerId) throws IOException;

  /**
   * Get the next set of values
   * @param scannerId clientId passed to openScanner
   * @param numberOfRows the number of rows to fetch
   * @return Array of Results (map of values); array is empty if done with this
   * region and null if we are NOT to go to the next region (happens when a
   * filter rules that the scan is done).
   * @throws IOException e
   */
  public Result[] next(long scannerId, int numberOfRows)
      throws IOException;

  /**
   * Close a scanner
   *
   * @param scannerId the scanner id returned by openScanner
   * @throws IOException e
   */
  public void close(long scannerId) throws IOException;

  /**
   * Opens a remote row lock.
   *
   * @param regionName name of region
   * @param row row to lock
   * @return lockId lock identifier
   * @throws IOException e
   */
  public long lockRow(final byte[] regionName, final byte[] row)
      throws IOException;

  /**
   * Releases a remote row lock.
   *
   * @param regionName region name
   * @param lockId the lock id returned by lockRow
   * @throws IOException e
   */
  public void unlockRow(final byte[] regionName, final long lockId)
      throws IOException;


  /**
   * Method used when a master is taking the place of another failed one.
   * @return All regions assigned on this region server
   * @throws IOException e
   */
  public HRegionInfo[] getRegionsAssignment() throws IOException;

  /**
   * Method used when a master is taking the place of another failed one.
   * @return The HSI
   * @throws IOException e
   */
  public HServerInfo getHServerInfo() throws IOException;

  /**
   * Method used for doing multiple actions(Deletes, Gets and Puts) in one call
   * @param multi
   * @return MultiResult
   * @throws IOException
   */
  public MultiResponse multiAction(MultiAction multi) throws IOException;

  /**
   * Multi put for putting multiple regions worth of puts at once.
   *
   * @param puts the request
   * @return the reply
   * @throws IOException e
   */
  public MultiPutResponse multiPut(MultiPut puts) throws IOException;

  /**
   * Bulk load an HFile into an open region
   */
  public void bulkLoadHFile(String hfilePath,
      byte[] regionName, byte[] familyName) throws IOException;
  public void bulkLoadHFile(String hfilePath,
      byte[] regionName, byte[] familyName, boolean assignSeqNum) throws IOException;

  /**
   * Closes the specified region.
   * @param hri region to be closed
   * @param reportWhenCompleted whether to report to master
   * @throws IOException
   */
  public void closeRegion(final HRegionInfo hri, final boolean reportWhenCompleted)
  throws IOException;

  /**
   * Update the assignment plan for each region server.
   * @param plan
   */
  public int updateFavoredNodes(AssignmentPlan plan)
      throws IOException;

  /**
   * Update the configuration.
   */
  public void updateConfiguration();

  /**
   * Stop this service.
   * @param why Why we're stopping.
   */
  @Override
  public void stop(String why);

  /** @return why we are stopping */
  @Override
  public String getStopReason();

  /**
   * Set the number of threads to be used for HDFS Quorum reads
   *
   * @param maxThreads quourum reads will be disabled if set to <= 0
   */
  public void setNumHDFSQuorumReadThreads(int maxThreads);

  /**
   * Set the amount of time we wait before initiating a second read when
   * using HDFS Quorum reads
   *
   * @param timeoutMillis
   */
  public void setHDFSQuorumReadTimeoutMillis(long timeoutMillis);

  /**
   * Returns the list of buckets which represent the uniform depth histogram
   * for a given region.
   * @param regionName
   * @return
   * @throws IOException
   */
  public List<Bucket> getHistogram(byte[] regionName) throws IOException;

  /**
   * Returns the list of buckets which represent the uniform depth histogram
   * for a given store.
   * @param regionName
   * @param family
   * @return
   * @throws IOException
   */
  public List<Bucket> getHistogramForStore(byte[] regionName, byte[] family)
      throws IOException;

  /**
   * Returns the list of buckets which represent the uniform depth histogram
   * for all the given regions
   * @param regionNames
   * @return
   * @throws IOException
   */
  public List<List<Bucket>> getHistograms(List<byte[]> regionNames)
      throws IOException;

  /*
   * Gets the location of the a particular row in a table.
   *
   * @param table
   * @param row
   * @param reload Should we reload the location cache? Set true if you get a
   *               network exception / NotServingRegionException.
   * @return
   * @throws IOException
   */
  public HRegionLocation getLocation(byte[] table, byte[] row, boolean reload)
      throws IOException;
}
