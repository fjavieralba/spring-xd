/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Definition of xdAdmin services.
 *
 * @author Gunnar Hillert
 * @author Ilayaperumal Gopinathan
 */
define(['angular'], function (angular) {
  'use strict';

  return angular.module('xdAdmin.services', [])
      .factory('JobDefinitions', function ($resource, $rootScope) {
        return $resource($rootScope.xdAdminServerUrl + '/jobs.json?deployments=true', {}, {
          query: {
            method: 'GET',
            isArray: true
          }
        });
      })
      .factory('JobDefinitionService', function ($resource, $log, $rootScope) {
        return {
          deploy: function (jobDefinition) {
            $log.info('Deploy Job ' + jobDefinition.name);
            $resource($rootScope.xdAdminServerUrl + '/jobs/' + jobDefinition.name, { 'deploy': true }, {
              deploy: { method: 'PUT' }
            }).deploy();
          },
          undeploy: function (jobDefinition) {
            $log.info('Undeploy Job ' + jobDefinition.name);
            $resource($rootScope.xdAdminServerUrl + '/jobs/' + jobDefinition.name, { 'deploy': false }, {
              undeploy: { method: 'PUT' }
            }).undeploy();
          }
        };
      })
      .factory('JobDeployments', function ($resource, $rootScope) {
        return $resource($rootScope.xdAdminServerUrl + '/batch/jobs.json', {}, {
          getArray: {method: 'GET', isArray: true}
        });
      })
      .factory('JobExecutions', function ($resource, $rootScope, $log) {
        return {
          getArray: function () {
            $log.info('Get Job Executions ');
            return $resource($rootScope.xdAdminServerUrl + '/batch/executions', {}, {
              getArray: {method: 'GET', isArray: true}
            }).getArray();
          },
          restart: function (jobExecution) {
            $log.info('Restart Job Execution' + jobExecution.executionId);
            return $resource($rootScope.xdAdminServerUrl + '/batch/executions/' + jobExecution.executionId, { 'restart': true }, {
              restart: { method: 'PUT' }
            }).restart();
          }
        };
      })
      .factory('JobLaunchService', function ($resource, growl, $rootScope) {
        return {
          convertToJsonAndSend: function (jobLaunchRequest) {

            console.log('Converting to Json: ' + jobLaunchRequest.jobName);
            console.log(jobLaunchRequest);
            //console.log('Model Change: ' + JSON.stringify(jobLaunchRequest.toJSON()));

            var jsonData = {};
            jobLaunchRequest.jobParameters.forEach(function (jobParameter) {

              var key = jobParameter.key;
              var value = jobParameter.value;
              var isIdentifying = jobParameter.isIdentifying;
              var dataType = jobParameter.type;
              var dataTypeToUse = '';

              if (typeof dataType !== 'undefined') {
                dataTypeToUse = '(' + dataType + ')';
              }

              if (isIdentifying) {
                jsonData['+' + key + dataTypeToUse] = value;
              }
              else {
                jsonData['-' + key + dataTypeToUse] = value;
              }
            });
            console.log(jsonData);
            var jsonDataAsString = JSON.stringify(jsonData);

            console.log(jsonDataAsString);

            this.launch(jobLaunchRequest.jobName, jsonDataAsString);
          },
          launch: function (jobName, jsonDataAsString) {
            console.log('Do actual Launch...');
            $resource($rootScope.xdAdminServerUrl + '/jobs/' + jobName + '/launch', { 'jobParameters': jsonDataAsString }, {
              launch: { method: 'PUT' }
            }).launch().$promise.then(
                function () {
                  growl.addSuccessMessage('Job ' + jobName + ' launched.');
                },
                function (data) {
                  console.error(data);
                  growl.addErrorMessage('Yikes, something bad happened while launching job ' + jobName);
                }
            );
          }
        };
      });
});


