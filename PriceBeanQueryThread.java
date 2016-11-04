package com.mbv.sale.service.impl;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import com.mbv.common.exception.MbvException;
import com.mbv.sale.bean.PriceAdjustConditionBean;
import com.mbv.sale.bean.VpPriceAdjustBean;
import com.mbv.sale.entity.PriceQueryLogsEntity;
import com.mbv.sale.service.PriceAdjustQueryService;
public class PriceBeanQueryThread extends Thread{
	private CountDownLatch latch;
	private PriceAdjustConditionBean bean;
	private PriceQueryLogsEntity logEntity;
	List<PriceQueryLogsEntity> queryLogList=null;
	private PriceAdjustQueryService priceAdjustQueryService;
	private VpPriceAdjustBean vpPriceAdjustBean;
	private Boolean queryFlag;//记录运算过程中是否有错误发生 如果错误则为true没有错误则为false
	public PriceBeanQueryThread(CountDownLatch latch, PriceAdjustQueryService priceAdjustQueryService, PriceQueryLogsEntity logEntity, PriceAdjustConditionBean bean) {
		this.latch= latch;
		this.priceAdjustQueryService = priceAdjustQueryService;
		this.logEntity= logEntity;
		this.bean = bean;
	}

	public List<PriceQueryLogsEntity> getQueryLogList() {
		return queryLogList;
	}

	public void setQueryLogList(List<PriceQueryLogsEntity> queryLogList) {
		this.queryLogList = queryLogList;
	}

	public VpPriceAdjustBean getVpPriceAdjustBean() {
		return vpPriceAdjustBean;
	}

	public void setVpPriceAdjustBean(VpPriceAdjustBean vpPriceAdjustBean) {
		this.vpPriceAdjustBean = vpPriceAdjustBean;
	}

	/**获取价格单*/
	@Override
	public void run() {
		try {
			vpPriceAdjustBean=priceAdjustQueryService.queryPriceAdjustByCondition(bean,logEntity);
			queryLogList=priceAdjustQueryService.getQueryLogList();
			latch.countDown();
			queryFlag=false;
		}catch (MbvException e) {
			latch.countDown();
			setLogErroInfo(logEntity, e.getMessage());
			queryFlag=true;
		}catch (Exception e) {
			latch.countDown();
			setLogErroInfo(logEntity, "系统内部错误:"+e.getMessage());
			queryFlag=true;
		}
	}
	/**
	 * 设置日志异常信息
	 * @param logEntity
	 */
	private void setLogErroInfo(PriceQueryLogsEntity logEntity,String errorMsg) {
		PriceQueryLogsEntity queryLogsEntity =new PriceQueryLogsEntity();
		queryLogsEntity.setReqStatus("0");
		queryLogsEntity.setLogInfo("价格单查询异常");
		queryLogsEntity.setLogError(errorMsg);
		queryLogsEntity.setCreateDate(new Date());
		queryLogsEntity.setReqId(logEntity.getReqId());//请求id
		queryLogsEntity.setReqDate(logEntity.getReqDate());//请求时间
		queryLogsEntity.setLogMethod(logEntity.getLogMethod());
		queryLogsEntity.setReqUser(logEntity.getReqUser());
		queryLogsEntity.setSellerCode(logEntity.getSellerCode());
		queryLogsEntity.setChannelCode(logEntity.getChannelCode());
		queryLogsEntity.setProdNum(logEntity.getProdNum());
		queryLogsEntity.setRetailPrice(logEntity.getRetailPrice());
		queryLogsEntity.setSaleType(logEntity.getSaleType());
		queryLogsEntity.setLogEndFlag("0");
		queryLogsEntity.setSaleDate(logEntity.getSaleDate());
		//将日志对象加入list
		queryLogList=priceAdjustQueryService.getQueryLogList();
		queryLogList.add(queryLogsEntity);
	}

	public Boolean getQueryFlag() {
		return queryFlag;
	}

	public void setQueryFlag(Boolean queryFlag) {
		this.queryFlag = queryFlag;
	}
	
	
}
