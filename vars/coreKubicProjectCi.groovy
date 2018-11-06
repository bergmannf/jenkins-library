// Copyright 2017 SUSE LINUX GmbH, Nuernberg, Germany.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
def call(Map params = [:]) {
    echo "Starting Kubic core project CI"
    // TODO: Don't hardcode salt repo name, find the right place
    // to lookup this information dynamically.
    githubCollaboratorCheck(
        org: 'kubic-project',
        repo: 'salt',
        user: env.CHANGE_AUTHOR,
        credentialsId: 'github-token')

    // Configure the job properties
    properties([
        buildDiscarder(logRotator(numToKeepStr: '15')),
        disableConcurrentBuilds(),
        parameters([
            booleanParam(name: 'ENVIRONMENT_DESTROY', defaultValue: true, description: 'Destroy env once done?'),
            // Could use validatingString to check for integer
            string(name: 'MASTER_COUNT', defaultValue: "3", description: 'Number of masters to start.'),
            string(name: 'WORKER_COUNT', defaultValue: "2", description: 'Number of workers to start.')
        ]),
    ])
    environment = env.getEnvironment()
    // The environmentTypeOptions are structs and can not easily be entered in the web ui. So instead of obtaining them from there, they will be passed as parameters directly.
    def environmentTypeOptions = params.get('environmentTypeOptions', null)
    boolean environmentDestroy = environment.get('ENVIRONMENT_DESTROY', 'true').toBoolean()
    int masterCount = environment.get('MASTER_COUNT', "3").toInteger()
    int workerCount = environment.get('WORKER_COUNT', "2").toInteger()

    withKubicEnvironment(
            nodeLabel: 'leap15.0&&caasp-pr-worker',
            environmentType: 'caasp-kvm',
            environmentTypeOptions: environmentTypeOptions,
            environmentDestroy: environmentDestroy,
            gitBase: 'https://github.com/kubic-project',
            gitBranch: env.getEnvironment().get('CHANGE_TARGET', env.BRANCH_NAME),
            gitCredentialsId: 'github-token',
            masterCount: masterCount,
            workerCount: workerCount) {

        // Run the Core Project Tests
        coreKubicProjectTests(
          environment: environment,
          podName: 'default'
        )

        // Run through the upgrade orchestration
        upgradeEnvironmentStage1(
            environment: environment,
            fakeUpdatesAvailable: true
        )

        upgradeEnvironmentStage2(
            environment: environment
        )

        // Run the Core Project Tests again
        coreKubicProjectTests(
          environment: environment,
          podName: 'default'
        )
    }
}
