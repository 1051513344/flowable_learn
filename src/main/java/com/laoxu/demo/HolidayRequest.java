package com.laoxu.demo;

import org.flowable.engine.*;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HolidayRequest {

  public static void main(String[] args) {
    // 连接数据库，并初始化
    ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
            .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1")
            .setJdbcUsername("sa")
            .setJdbcPassword("")
            .setJdbcDriver("org.h2.Driver")
            .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
    // 加载流程xml文件
    ProcessEngine processEngine = cfg.buildProcessEngine();
    RepositoryService repositoryService = processEngine.getRepositoryService();
    Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("holiday-request.bpmn20.xml")
            .deploy();
    // 在xml中查找流程，process标签中的name属性
    ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
            .deploymentId(deployment.getId())
            .singleResult();
    System.out.println("Found process definition : " + processDefinition.getName());

    // 输入变量
    Scanner scanner= new Scanner(System.in);

    System.out.println("Who are you?");
    String employee = scanner.nextLine();

    System.out.println("How many holidays do you want to request?");
    Integer nrOfHolidays = Integer.valueOf(scanner.nextLine());

    System.out.println("Why do you need them?");
    String description = scanner.nextLine();

    // 1.填充变量，启动流程
    RuntimeService runtimeService = processEngine.getRuntimeService();
    // 定义任务中使用到的变量
    Map<String, Object> variables = new HashMap<String, Object>();
    // <userTask id="holidayApprovedTask" name="Holiday approved" flowable:assignee="${employee}" />
    variables.put("employee", employee);
    variables.put("nrOfHolidays", nrOfHolidays);
    variables.put("description", description);
    // <process id="holidayRequest" name="Holiday Request" isExecutable="true">
    ProcessInstance processInstance =
            runtimeService.startProcessInstanceByKey("holidayRequest", variables);
    // 2.执行任务
    TaskService taskService = processEngine.getTaskService();
    // <userTask id="approveTask" name="Approve or reject request" flowable:candidateGroups="managers" />
    List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
    System.out.println("You have " + tasks.size() + " tasks:");
    for (int i=0; i<tasks.size(); i++) {
      System.out.println((i+1) + ") " + tasks.get(i).getName());
    }

    // 3.进入经理流程
    System.out.println("Which task would you like to complete?");
    int taskIndex = Integer.valueOf(scanner.nextLine());
    Task task = tasks.get(taskIndex - 1);
    Map<String, Object> processVariables = taskService.getVariables(task.getId());
    // <userTask id="holidayApprovedTask" name="Holiday approved" flowable:assignee="${employee}" />
    System.out.println(processVariables.get("employee") + " wants " +
            processVariables.get("nrOfHolidays") + " of holidays. Do you approve this?");

    // 4.进入网关流程
    boolean approved = scanner.nextLine().toLowerCase().equals("y");
    variables = new HashMap<String, Object>();
    variables.put("approved", approved);
    /**
     * 根据approved值
     * true  执行后会调用 {@link org.flowable.CallExternalSystemDelegate#execute(DelegateExecution)}
     * false 执行后会调用 {@link org.flowable.SendRejectionMail#execute(DelegateExecution)}
     */
    taskService.complete(task.getId(), variables);

    // 5.历史数据服务
    HistoryService historyService = processEngine.getHistoryService();
    List<HistoricActivityInstance> activities =
            historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .finished()
                    .orderByHistoricActivityInstanceEndTime().asc()
                    .list();
    // 6.获取每个历史节点经历的时间
    for (HistoricActivityInstance activity : activities) {
      System.out.println(activity.getActivityId() + " took "
              + activity.getDurationInMillis() + " milliseconds");
    }


  }

}