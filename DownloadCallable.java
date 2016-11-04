package com.mbv.web.rest.util;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mbv.biz.config.bean.DegBean;
import com.mbv.biz.config.bean.OrderBean;
import com.mbv.biz.config.service.ReportService;

public class DownloadCallable implements Callable<Object>{
	
    private  Logger log = LoggerFactory.getLogger(DownloadCallable.class);
	
	private ReportService reportService;
	List<OrderBean> orderList = null;
	private DegBean bean;

	private String taskNum;
	
	public DownloadCallable(String taskNum, ReportService reportService, DegBean bean) {
		this.taskNum = taskNum;
		this.reportService = reportService;
		this.bean = bean;
	}
	
	public Object call() throws Exception {
		log.info(">>>" + taskNum + "任务启动");
	   Date dateTmp1 = new Date();
	   Date dateTmp2 = new Date();
	   orderList = reportService.queryOrderByParamsForDownloadPage(bean);
	   long time = dateTmp2.getTime() - dateTmp1.getTime();
	   log.info(taskNum + "任务返回运行结果,当前任务时间【" + time + "毫秒】");
	   log.info(">>>" + taskNum + "任务终止");
	   return orderList;
	}
}
