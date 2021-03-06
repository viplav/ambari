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
'use strict';

angular.module('ambariAdminConsole')
.factory('Auth',['$http', 'Settings', '$window', function($http, Settings, $window) {
  var ambari;
  var currentUserName;
  if (localStorage.ambari) {
    ambari = JSON.parse(localStorage.ambari);
    if (ambari && ambari.app && ambari.app.loginName) {
      currentUserName = ambari.app.loginName;
    }
  }
  return {
    signout: function() {
      // Workaround for sign off within Basic Authorization
      var origin = $window.location.protocol + '//' + Date.now() + ':' + Date.now() + '@' +
            $window.location.hostname + ($window.location.port ? ':' + $window.location.port : '');
      return $http({
        method: 'GET',
        url: origin + Settings.baseUrl + '/logout'
      });
    },
    getCurrentUser: function() {
    	return currentUserName;
    }
  };
}]);
