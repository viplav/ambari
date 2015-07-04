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

import Ember from 'ember';

export default Ember.Controller.extend({
  openQueries: Ember.inject.controller(),
  index: Ember.inject.controller(),

  settingsService: Ember.inject.service('settings'),

  predefinedSettings: Ember.computed.alias('settingsService.predefinedSettings'),
  settings: Ember.computed.alias('settingsService.settings'),

  init: function() {
    this._super();

    this.get('settingsService').loadDefaultSettings();
  },

  excluded: function() {
    var settings = this.get('settings');

    return this.get('predefinedSettings').filter(function(setting) {
      return settings.findBy('key.name', setting.name);
    });
  }.property('settings.@each.key'),

  parseGlobalSettings: function () {
    this.get('settingsService').parseGlobalSettings(this.get('openQueries.currentQuery'), this.get('index.model'));
  }.observes('openQueries.currentQuery', 'openQueries.currentQuery.fileContent', 'openQueries.tabUpdated').on('init'),

  actions: {
    add: function () {
      this.get('settingsService').add();
    },

    remove: function (setting) {
      this.get('settingsService').remove(setting);
    },

    addKey: function (name) {
      this.get('settingsService').createKey(name);
    },

    removeAll: function () {
      this.get('settingsService').removeAll();
    },

    saveDefaultSettings: function() {
      this.get('settingsService').saveDefaultSettings();
    }
  }
});
