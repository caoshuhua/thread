package com.mbv.web.rest.util;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mbv.biz.config.bean.DegBean;
import com.mbv.biz.config.bean.OrderBean;
import com.mbv.biz.config.service.ReportService;
import com.mbv.common.exception.MbvException;

public class DownloadProductInfoThread extends Thread{

	private  Logger log = LoggerFactory.getLogger(DownloadProductInfoThread.class);
	
	private CountDownLatch latch;
	private ReportService reportService;
	List<OrderBean> orderList = null;
	private DegBean bean;
	
	public DownloadProductInfoThread(CountDownLatch latch, ReportService reportService, DegBean bean) {
		this.latch= latch;
		this.reportService = reportService;
		this.bean = bean;
	}
	
	@Override
	public void run() {
		try {
			log.info("bean:"+bean);
			orderList = reportService.queryOrderByParamsForDownloadPage(bean);
			latch.countDown();
		}catch (MbvException e) {
			latch.countDown();
		}catch (Exception e) {
			latch.countDown();
		}
	}

	public CountDownLatch getLatch() {
		return latch;
	}

	public void setLatch(CountDownLatch latch) {
		this.latch = latch;
	}

	public ReportService getReportService() {
		return reportService;
	}

	public void setReportService(ReportService reportService) {
		this.reportService = reportService;
	}

	public List<OrderBean> getOrderList() {
		return orderList;
	}

	public void setOrderList(List<OrderBean> orderList) {
		this.orderList = orderList;
	}

	public DegBean getBean() {
		return bean;
	}

	public void setBean(DegBean bean) {
		this.bean = bean;
	}
	
}
