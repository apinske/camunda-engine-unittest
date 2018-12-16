/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.unittest;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineAssertions.assertThat;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.complete;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.managementService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.runtimeService;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.task;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.management.JobDefinition;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * @author Daniel Meyer
 * @author Martin Schimak
 */
public class SimpleTestCase {

    @Rule
    public ProcessEngineRule rule = new ProcessEngineRule();

    @Test
    @Deployment(resources = { "Signal.bpmn" })
    public void shouldExecuteProcess() throws Exception {
        ProcessInstance processInstance = runtimeService().startProcessInstanceByKey("Signal");
        dumpProcessState(processInstance.getId());

        Task task1 = task("UserTask_1", processInstance);
        assertThat(task1).isNotNull();

        Task task2 = task("UserTask_2", processInstance);
        assertThat(task2).isNotNull();

        runtimeService().setVariableLocal(task2.getExecutionId(), "var", "val");
        dumpProcessState(processInstance.getId());

        complete(task1);
        dumpProcessState(processInstance.getId());

        Task task3 = task("UserTask_3", processInstance);
        assertThat(task3).isNotNull();
        assertEquals("val", runtimeService().getVariableLocal(task3.getExecutionId(), "var"));
    }

    private void dumpProcessState(String processInstanceId) throws Exception {
        List<Execution> executions = runtimeService().createExecutionQuery().processInstanceId(processInstanceId).list();
        StringBuilder sb = new StringBuilder();
        executions.stream().filter(e -> ((ExecutionEntity) e).getParentId() == null).findAny()
                .ifPresent(e -> printExecution(sb, 4, e, executions));
        System.out.println(sb);
    }

    private void printExecution(StringBuilder sb, int indent, Execution execution, List<Execution> executions) {
        RuntimeService runtime = runtimeService();
        ManagementService management = managementService();
        sb.append('\n');
        repeat(sb, indent, ' ');
        sb.append(execution);
        if (((ExecutionEntity) execution).getCurrentActivityId() != null) {
            sb.append(" in ");
            sb.append(((ExecutionEntity) execution).getCurrentActivityId());
        }
        if (((ExecutionEntity) execution).getCurrentTransitionId() != null) {
            sb.append(" at ");
            sb.append(((ExecutionEntity) execution).getCurrentTransitionId());
        }
        runtime.getVariablesLocal(execution.getId()).forEach((k, v) -> {
            sb.append('\n');
            repeat(sb, indent, ' ');
            sb.append("- Variable '");
            sb.append(k);
            sb.append("' = ");
            sb.append(v);
        });
        runtime.createEventSubscriptionQuery().executionId(execution.getId()).list().forEach(e -> {
            sb.append('\n');
            repeat(sb, indent, ' ');
            sb.append("- EventSubscription[");
            sb.append(e.getId());
            sb.append("] for ");
            sb.append(e.getEventName());
            sb.append(" in ");
            sb.append(e.getActivityId());
        });
        management.createJobQuery().executionId(execution.getId()).list().forEach(j -> {
            sb.append('\n');
            repeat(sb, indent, ' ');
            sb.append("- Job[");
            sb.append(j.getId());
            sb.append("] ");
            JobDefinition jobDef = management.createJobDefinitionQuery().jobDefinitionId(j.getJobDefinitionId()).singleResult();
            sb.append(jobDef.getJobType());
            sb.append(" (");
            sb.append(jobDef.getJobConfiguration());
            sb.append(")");
        });
        executions.stream().filter(e -> execution.getId().equals(((ExecutionEntity) e).getParentId()))
                .forEach(e -> printExecution(sb, indent + 4, e, executions));
    }

    private void repeat(StringBuilder sb, int count, char c) {
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
    }

}
