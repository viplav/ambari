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
package org.apache.ambari.server.controller.internal;

import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.ambari.server.api.services.AmbariMetaInfo;
import org.apache.ambari.server.configuration.ComponentSSLConfiguration;
import org.apache.ambari.server.controller.jmx.JMXHostProvider;
import org.apache.ambari.server.controller.jmx.JMXPropertyProvider;
import org.apache.ambari.server.controller.metrics.MetricHostProvider;
import org.apache.ambari.server.controller.metrics.MetricsPropertyProvider;
import org.apache.ambari.server.controller.metrics.MetricsServiceProvider;
import org.apache.ambari.server.controller.spi.Predicate;
import org.apache.ambari.server.controller.spi.PropertyProvider;
import org.apache.ambari.server.controller.spi.Request;
import org.apache.ambari.server.controller.spi.Resource;
import org.apache.ambari.server.controller.spi.SystemException;
import org.apache.ambari.server.controller.utilities.StreamProvider;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.StackId;
import org.apache.ambari.server.state.stack.Metric;
import org.apache.ambari.server.state.stack.MetricDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.apache.ambari.server.controller.metrics.MetricsServiceProvider.MetricsService;

/**
 * This class analyzes a service's metrics to determine if additional
 * metrics should be fetched.  It's okay to maintain state here since these
 * are done per-request.
 */
public class StackDefinedPropertyProvider implements PropertyProvider {
  private static final Logger LOG = LoggerFactory.getLogger(StackDefinedPropertyProvider.class);

  @Inject
  private static Clusters clusters = null;
  @Inject
  private static AmbariMetaInfo metaInfo = null;
  @Inject
  private static Injector injector = null;


  private Resource.Type type = null;
  private String clusterNamePropertyId = null;
  private String hostNamePropertyId = null;
  private String componentNamePropertyId = null;
  private String resourceStatePropertyId = null;
  private ComponentSSLConfiguration sslConfig = null;
  private StreamProvider streamProvider = null;
  private JMXHostProvider jmxHostProvider;
  private PropertyProvider defaultJmx = null;
  private PropertyProvider defaultGanglia = null;

  private final MetricHostProvider metricHostProvider;
  private final MetricsServiceProvider metricsServiceProvider;
  private MetricsService metricsService = MetricsService.GANGLIA;

  /**
   * PropertyHelper/AbstractPropertyProvider expect map of maps,
   * that's why we wrap metrics into map
   */
  public static final String WRAPPED_METRICS_KEY = "WRAPPED_METRICS_KEY";

  @Inject
  public static void init(Injector injector) {
    clusters = injector.getInstance(Clusters.class);
    metaInfo = injector.getInstance(AmbariMetaInfo.class);
    StackDefinedPropertyProvider.injector = injector;
  }

  public StackDefinedPropertyProvider(Resource.Type type,
                                      JMXHostProvider jmxHostProvider,
                                      MetricHostProvider metricHostProvider,
                                      MetricsServiceProvider serviceProvider,
                                      StreamProvider streamProvider,
                                      String clusterPropertyId,
                                      String hostPropertyId,
                                      String componentPropertyId,
                                      String resourceStatePropertyId,
                                      PropertyProvider defaultJmxPropertyProvider,
                                      PropertyProvider defaultGangliaPropertyProvider) {

    this.metricHostProvider = metricHostProvider;
    this.metricsServiceProvider = serviceProvider;

    if (null == clusterPropertyId)
      throw new NullPointerException("Cluster name property id cannot be null");
    if (null == componentPropertyId)
      throw new NullPointerException("Component name property id cannot be null");

    this.type = type;

    clusterNamePropertyId = clusterPropertyId;
    hostNamePropertyId = hostPropertyId;
    componentNamePropertyId = componentPropertyId;
    this.resourceStatePropertyId = resourceStatePropertyId;
    this.jmxHostProvider = jmxHostProvider;
    sslConfig = ComponentSSLConfiguration.instance();
    this.streamProvider = streamProvider;
    defaultJmx = defaultJmxPropertyProvider;
    defaultGanglia = defaultGangliaPropertyProvider;
  }


  public StackDefinedPropertyProvider(Resource.Type type,
                                      MetricsService metricsService,
                                      JMXHostProvider jmxHostProvider,
                                      MetricHostProvider metricHostProvider,
                                      MetricsServiceProvider serviceProvider,
                                      StreamProvider streamProvider,
                                      String clusterPropertyId,
                                      String hostPropertyId,
                                      String componentPropertyId,
                                      String jmxStatePropertyId,
                                      PropertyProvider defaultJmxPropertyProvider,
                                      PropertyProvider defaultGangliaPropertyProvider) {

    this(type, jmxHostProvider, metricHostProvider, serviceProvider,
      streamProvider, clusterPropertyId, hostPropertyId, componentPropertyId,
      jmxStatePropertyId, defaultJmxPropertyProvider, defaultGangliaPropertyProvider);

    this.metricsService = metricsService;
  }


  @Override
  public Set<Resource> populateResources(Set<Resource> resources,
      Request request, Predicate predicate) throws SystemException {

    // only arrange for one instance of Ganglia and JMX instantiation
    Map<String, Map<String, PropertyInfo>> gangliaMap = new HashMap<String, Map<String, PropertyInfo>>();
    Map<String, Map<String, PropertyInfo>> jmxMap = new HashMap<String, Map<String, PropertyInfo>>();

    List<PropertyProvider> additional = new ArrayList<PropertyProvider>();

    try {
      for (Resource r : resources) {
        String clusterName = r.getPropertyValue(clusterNamePropertyId).toString();
        String componentName = r.getPropertyValue(componentNamePropertyId).toString();

        Cluster cluster = clusters.getCluster(clusterName);
        StackId stack = cluster.getDesiredStackVersion();
        String svc = metaInfo.getComponentToService(stack.getStackName(),
            stack.getStackVersion(), componentName);

        List<MetricDefinition> defs = metaInfo.getMetrics(
            stack.getStackName(), stack.getStackVersion(), svc, componentName, type.name());

        if (null == defs || 0 == defs.size())
          continue;

        for (MetricDefinition m : defs) {
          if (m.getType().equals("ganglia")) {
            gangliaMap.put(componentName, getPropertyInfo(m));
          } else if (m.getType().equals("jmx")) {
            jmxMap.put(componentName, getPropertyInfo(m));
          } else {
            PropertyProvider pp = getDelegate(m,
                streamProvider, metricHostProvider,
                clusterNamePropertyId, hostNamePropertyId,
                componentNamePropertyId, resourceStatePropertyId,
                componentName);
            if (pp == null) {
              pp = getDelegate(m);
            }
            if (pp != null) {
              additional.add(pp);
            }

          }
        }
      }

      if (gangliaMap.size() > 0) {
        PropertyProvider propertyProvider =
          MetricsPropertyProvider.createInstance(type, gangliaMap,
            streamProvider, sslConfig, metricHostProvider,
            metricsServiceProvider, clusterNamePropertyId,
            hostNamePropertyId, componentNamePropertyId);

        propertyProvider.populateResources(resources, request, predicate);
      } else {
        defaultGanglia.populateResources(resources, request, predicate);
      }

      if (jmxMap.size() > 0) {
        JMXPropertyProvider jpp = new JMXPropertyProvider(jmxMap, streamProvider,
            jmxHostProvider, metricHostProvider,
            clusterNamePropertyId, hostNamePropertyId,
            componentNamePropertyId, resourceStatePropertyId);

        jpp.populateResources(resources, request, predicate);
      } else {
        defaultJmx.populateResources(resources, request, predicate);
      }

      for (PropertyProvider pp : additional) {
        pp.populateResources(resources, request, predicate);
      }

    } catch (Exception e) {
      e.printStackTrace();
      throw new SystemException("Error loading deferred resources", e);
    }

    return resources;
  }

  @Override
  public Set<String> checkPropertyIds(Set<String> propertyIds) {
    return Collections.emptySet();
  }

  /**
   * @param def the metric definition
   * @return the converted Map required for JMX or Ganglia execution.
   * Format: <metric name, property info>
   */
  public static Map<String, PropertyInfo> getPropertyInfo(MetricDefinition def) {
    Map<String, PropertyInfo> defs = new HashMap<String, PropertyInfo>();

    for (Entry<String, Metric> entry : def.getMetrics().entrySet()) {
      Metric metric = entry.getValue();
      if (metric.getName() != null) {
        PropertyInfo propertyInfo = new PropertyInfo(metric.getName(),
                metric.isTemporal(), metric.isPointInTime());
        propertyInfo.setAmsHostMetric(metric.isAmsHostMetric());
        propertyInfo.setUnit(metric.getUnit());
        defs.put(entry.getKey(), propertyInfo);
      }
    }

    return defs;
  }

  /**
   * @param definition metric definition for a component and resource type combination
   * @return the custom property provider
   */
  private PropertyProvider getDelegate(MetricDefinition definition) {
    try {
      Class<?> clz = Class.forName(definition.getType());

      // singleton/factory
      try {
        Method m = clz.getMethod("getInstance", Map.class, Map.class);
        Object o = m.invoke(null, definition.getProperties(), definition.getMetrics());
        return PropertyProvider.class.cast(o);
      } catch (Exception e) {
        LOG.info("Could not load singleton or factory method for type '" +
            definition.getType());
      }

      // try maps constructor
      try {
        Constructor<?> ct = clz.getConstructor(Map.class, Map.class);
        Object o = ct.newInstance(definition.getProperties(), definition.getMetrics());
        return PropertyProvider.class.cast(o);
      } catch (Exception e) {
        LOG.info("Could not find contructor for type '" +
            definition.getType());
      }

      // just new instance
      return PropertyProvider.class.cast(clz.newInstance());

    } catch (Exception e) {
      LOG.error("Could not load class " + definition.getType());
      return null;
    }
  }

  /**
   *
   * @param definition the metric definition for a component
   * @param streamProvider the stream provider
   * @param metricsHostProvider the metrics host provider
   * @param clusterNamePropertyId the cluster name property id
   * @param hostNamePropertyId the host name property id
   * @param componentNamePropertyId the component name property id
   * @param statePropertyId the state property id
   * @return the custom property provider
   */

  private PropertyProvider getDelegate(MetricDefinition definition,
                                       StreamProvider streamProvider,
                                       MetricHostProvider metricsHostProvider,
                                       String clusterNamePropertyId,
                                       String hostNamePropertyId,
                                       String componentNamePropertyId,
                                       String statePropertyId,
                                       String componentName) {
    Map<String, PropertyInfo> metrics = getPropertyInfo(definition);
    HashMap<String, Map<String, PropertyInfo>> componentMetrics =
        new HashMap<String, Map<String, PropertyInfo>>();
    componentMetrics.put(WRAPPED_METRICS_KEY, metrics);

    try {
      Class<?> clz = Class.forName(definition.getType());
      try {
         /*
         * Warning: this branch is already used, that's why please adjust
         * all implementations when modifying constructor interface
         */
        Constructor<?> ct = clz.getConstructor(Injector.class, Map.class,
            Map.class, StreamProvider.class, MetricHostProvider.class,
            String.class, String.class, String.class, String.class, String.class);
        Object o = ct.newInstance(
            injector,
            definition.getProperties(), componentMetrics,
            streamProvider, metricsHostProvider,
            clusterNamePropertyId, hostNamePropertyId,
            componentNamePropertyId, statePropertyId, componentName);
        return PropertyProvider.class.cast(o);
      } catch (Exception e) {
        LOG.info("Could not find contructor for type '" +
            definition.getType());
      }

      // just new instance
      return PropertyProvider.class.cast(clz.newInstance());

    } catch (Exception e) {
      LOG.error("Could not load class " + definition.getType());
      return null;
    }


  }

}
