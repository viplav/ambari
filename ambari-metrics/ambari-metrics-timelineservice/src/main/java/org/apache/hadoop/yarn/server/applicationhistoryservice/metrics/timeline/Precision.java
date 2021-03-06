/**
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
package org.apache.hadoop.yarn.server.applicationhistoryservice.metrics.timeline;

/**
 * Is used to determine metrics aggregate table.
 *
 * @see org.apache.hadoop.yarn.server.applicationhistoryservice.webapp.TimelineWebServices#getTimelineMetric
 */
public enum Precision {
  SECONDS,
  MINUTES,
  HOURS,
  DAYS;

  public static class PrecisionFormatException extends IllegalArgumentException {
    public PrecisionFormatException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static Precision getPrecision(String precision) throws PrecisionFormatException {
    if (precision == null ) {
      return null;
    }
    try {
      return Precision.valueOf(precision.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new PrecisionFormatException("precision should be seconds, " +
        "minutes, hours or days", e);
    }
  }
}
