package com.mbv.sale.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Resource;
import javax.jms.Destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.mbv.common.constant.MbvConstant;
import com.mbv.common.exception.MbvException;
import com.mbv.sale.bean.PriceAdjustConditionBean;
import com.mbv.sale.bean.ResultBean;
import com.mbv.sale.bean.VpPriceAdjustBean;
import com.mbv.sale.entity.PriceQueryLogsEntity;
import com.mbv.sale.entity.PriceQueryLogsEntityDtl;
import com.mbv.sale.entity.ProtectedSettlementEntity;
import com.mbv.sale.entity.ProtectedSettlementProdEntity;
import com.mbv.sale.entity.VpPriceAdjustGoodsEntity;
import com.mbv.sale.entity.VpPriceAdjustSettlementRulesEntity;
import com.mbv.sale.service.PriceAdjustQueryService;
import com.mbv.sale.service.PriceProtectedQueryService;
import com.mbv.sale.service.PriceQueryLogsService;
import com.mbv.sale.service.PriceQueryMQService;
import com.mbv.sale.service.PriceQueryService;
/**
 * @author Sean
 *价格查询服务
 */
/**
 * @author Sean
 *
 */
@Service("priceQueryService")
public class PriceQueryServiceImpl implements PriceQueryService{
	private Logger log = LoggerFactory.getLogger("priceQuery");
	List<PriceQueryLogsEntity> queryLogList=null;
	@Autowired
	@Qualifier("logQueue")
	private Destination logQueue; //日志队列
	@Autowired
	@Qualifier("priceQueryQueue")
	private Destination priceQueryQueue;//价格查询队列
	@Resource 
	PriceQueryMQService priceQueryMQService;//MQ服务
	@Resource 
	PriceAdjustQueryService priceAdjustQueryService;//价格单运算服务
	@Resource 
	PriceProtectedQueryService priceProtectedQueryService;//保底单运算服务
	@Resource
	private PriceQueryLogsService priceQueryLogsService;//日志服务
	
	@SuppressWarnings("serial")
	Map<String,String> settlementTypeDesc=new HashMap<String,String>(){
		{
			put("fixed_point","固定扣点");
			put("step_point","阶梯分成");
			put("specified_price","指定价格");
		}
	};
	@SuppressWarnings("serial")
	Map<String,String> dimensionDesc=new HashMap<String,String>(){
		{
			put("seller_dimension","商家维度");
			put("brand_dimension","品牌维度");
			put("code_dimension","商品款码(6位码)");
			put("sku_dimension","商家编码(11位码)");
		}
	};
	@Override
	public ResultBean priceQueryHandler(PriceAdjustConditionBean bean,String method) throws MbvException {
		double returnPrice=0;
		String reqId="";
		ResultBean resultBean = new ResultBean();
		PriceQueryLogsEntity logEntity = new PriceQueryLogsEntity();
		queryLogList=new ArrayList<PriceQueryLogsEntity>();//日志list
		//创建两线程一个取价格单线程，一个取保底单线程
		CountDownLatch latch=new CountDownLatch(2);
		String jsonLogs ="";//日志信息
		long start;//开始
		long end;//结束
		//获取唯一符合条件的价格调整单
		Long logId=null;//日志ID
		try {
			//判断是同步还是异步方法
			if("sync".equals(method)){
				reqId=priceQueryLogsService.getReqId(MbvConstant.SEQ_NAME_PRICEQUERYLOGS);
			}else if("async".equals(method)){
				reqId=bean.getReqId();
			}
			resultBean.setReqId(reqId);
			/*设置日志基本信息*/
			logEntity.setReqId(reqId);//请求id
			logEntity.setReqDate(new Date(System.currentTimeMillis()));//请求时间
			logEntity.setLogMethod(method);//同步方法
			logEntity.setLogEndFlag("0");//设置日志结束的标志0未结束，1结束
			//查询条件校验
			bean=priceAdjustQueryService.validQueryCondition(bean);
			/*设置日志查询条件*/
			logEntity=setLogQueryInfo(logEntity,bean);
			
			//取价格单线程
			PriceBeanQueryThread priceBeanQueryThread = new PriceBeanQueryThread(latch,priceAdjustQueryService,logEntity,bean);
			//取保底单线程
			PriceProtectedQueryThread priceProtectedQueryThread = new PriceProtectedQueryThread(latch,priceProtectedQueryService,logEntity,bean);
			/*开始获取价格单*/
			start = System.currentTimeMillis();
			//启动两个线程去分别查询价格单和保底单
			priceBeanQueryThread.start();
			priceProtectedQueryThread.start();
			//等待线程完成
			latch.await();
			//价格单
			VpPriceAdjustBean priceBean = priceBeanQueryThread.getVpPriceAdjustBean();
			//价格单查询日志
			List<PriceQueryLogsEntity> priceQueryLogs = priceBeanQueryThread.getQueryLogList();
			//保底单
			ProtectedSettlementEntity priceProtectedBean = priceProtectedQueryThread.getProtectedEntity();
			//保底单查询日志
			List<PriceQueryLogsEntity> priceProtectedQueryLogs = priceProtectedQueryThread.getQueryLogList();
			//保底日志对象
			queryLogList.addAll(priceQueryLogs);
			queryLogList.addAll(priceProtectedQueryLogs);
			
			Boolean pirceQueryFlag = priceBeanQueryThread.getQueryFlag();//价格计算结果
			Boolean protectedQueryFlag = priceProtectedQueryThread.getQueryFlag();//保底计算结果
			
			if(pirceQueryFlag||protectedQueryFlag){
				throw new MbvException("运算失败!");
			}
			//VpPriceAdjustBean adjustBean = queryPriceAdjustByCondition(bean,"sync",logEntity);
			end = System.currentTimeMillis();
			log.info("获取价格单用时:"+(end-start)+"ms");
			if(priceBean!=null){
				resultBean.setAdjustNum(priceBean.getAdjustNum());
				logEntity.setAdjustNum(priceBean.getAdjustNum());
				resultBean.setPurchaseOrg(priceBean.getPurchaseOrg());
				//判断价格单
				if(priceProtectedBean==null){
					//保底单为null
					//计算结算价
					returnPrice=calculateFinalPrice(priceBean, bean,logEntity,null);
					resultBean.setReturnPrice(returnPrice);
					logEntity.setReturnPrice(returnPrice);
				}else if(priceProtectedBean!=null){
					//需要先计算保底价
					String protectedNum = priceProtectedBean.getSettlementNum();
					resultBean.setProtectNum(protectedNum);
					logEntity.setProtectedNum(protectedNum);
					//计算保底价返回保底单、最低零售价、保底结算价
					Map<String,Object> map=calculateProtectPrice(priceProtectedBean,bean,logEntity);
					//计算结算价
					returnPrice=calculateFinalPrice(priceBean, bean,logEntity,map);
					resultBean.setReturnPrice(returnPrice);
					logEntity.setReturnPrice(returnPrice);
				}
				logId = setLogEndInfo(logEntity, "查询结束!","1");
			}else{
				resultBean.setError(true);
				resultBean.setErrorMsg("无价格单定义");
				logId = setLogEndInfo(logEntity,"无价格单定义","0");
				log.info("该查询条件无价格单定义，请配置价格单");
			}
			//查询接口完成后把日志信息放到MQ消息队列中
			if(logId!=null){
				jsonLogs = logListToJson(queryLogList,logId);
				priceQueryMQService.sendMessage(logQueue, jsonLogs);
			}else{
				log.info(logEntity.getReqId()+":插入日志失败!");
			}
			queryLogList.clear();
		}catch (Exception e) {
			resultBean.setError(true);
			String errorMsg="";
			if(e instanceof MbvException){
				errorMsg=e.getMessage();
			}
			resultBean.setErrorMsg("系统内部错误 "+errorMsg);
			logId=setLogEndInfo(logEntity, "系统内部错误 "+errorMsg,"0");
			if(logId!=null){
				jsonLogs = logListToJson(queryLogList,logId);
				priceQueryMQService.sendMessage(logQueue, jsonLogs);
			}else{
				log.info(logEntity.getReqId()+":插入日志失败!");
			}
			queryLogList.clear();
			log.info("查询价格异常,系统内部错误:"+e.getMessage());
		}
		return resultBean;
	}
	private String logListToJson(List<PriceQueryLogsEntity> queryLogList, Long logId) {
		PriceQueryLogsEntityDtl logsEntityDtl=null;
		ArrayList<PriceQueryLogsEntityDtl> list = new ArrayList<PriceQueryLogsEntityDtl>();
		for (PriceQueryLogsEntity logsEntity : queryLogList) {
			logsEntityDtl= new PriceQueryLogsEntityDtl();
			logsEntityDtl.setLogId(logId);
			logsEntityDtl.setLogInfo(logsEntity.getLogInfo());
			logsEntityDtl.setCreateDate(logsEntity.getCreateDate());
			list.add(logsEntityDtl);
		}
		String jsonStr = JSONObject.toJSONString(list, SerializerFeature.WriteDateUseDateFormat ,SerializerFeature.DisableCircularReferenceDetect);
		return jsonStr;
	}
	private Long setLogEndInfo(PriceQueryLogsEntity logEntity, String msg, String status) {
		PriceQueryLogsEntity queryLogsEntity =new PriceQueryLogsEntity();
		queryLogsEntity.setReqStatus(status);
		if("0".equals(status)){
			queryLogsEntity.setLogInfo("价格查询异常!");
			queryLogsEntity.setLogError(msg);
		}else{
			queryLogsEntity.setLogInfo(msg);
		}
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
		queryLogsEntity.setProtectedNum(logEntity.getProtectedNum());
		queryLogsEntity.setAdjustNum(logEntity.getAdjustNum());
		queryLogsEntity.setReturnPrice(logEntity.getReturnPrice());
		queryLogsEntity.setSaleDate(logEntity.getSaleDate());
		queryLogsEntity.setLogEndFlag("1");
		//插入日志主表
		int count = priceQueryLogsService.insertQueryLogs(queryLogsEntity);
		if(count<=0){
			return null;
		}else{
			return queryLogsEntity.getId();
		}
		
		//将日志对象加入list
		//queryLogList.add(queryLogsEntity);
		
	}
	private PriceQueryLogsEntity setLogQueryInfo(PriceQueryLogsEntity logEntity, PriceAdjustConditionBean bean) {
		logEntity.setReqUser(bean.getReqUser());
		logEntity.setSellerCode(bean.getSellerCode());
		logEntity.setChannelCode(bean.getSalesRangesValue());
		logEntity.setProdNum(bean.getGoodSku());
		logEntity.setRetailPrice(bean.getRetailPrice());
		logEntity.setSaleType(bean.getSaleType());
		logEntity.setSaleDate(bean.getSaleDate());
		return logEntity;
	}
	
	/*
	 * 异步查询价格
	 * **/
	@Override
	public ResultBean queryPriceAsyncByParams(PriceAdjustConditionBean bean) throws MbvException {
		ResultBean resultBean=new ResultBean();
		try {
			log.info("--------异步接口查询价格----------");
			String reqId=priceQueryLogsService.getReqId(MbvConstant.SEQ_NAME_PRICEQUERYLOGS);
			resultBean.setReqId(reqId);
			bean.setReqId(reqId);
			String queryJson = JSONObject.toJSONString(bean,SerializerFeature.WriteDateUseDateFormat);
			log.info("查询条件:"+queryJson);
			//将查询条件放到价格查询消息队列
			priceQueryMQService.sendMessage(priceQueryQueue, queryJson);
			log.info("--------异步接口查询价格接口调用成功----------");
		} catch (Exception e) {
			log.info("--------异步接口查询价格接口调用失败----------");
			resultBean.setError(true);
			resultBean.setErrorMsg("调用异步查询接口失败！:"+e.getMessage());
		}
		return resultBean;
	}
	/*
	 * 同步查询价格
	 * */
	@Override
	public ResultBean queryPriceSyncByParams(PriceAdjustConditionBean bean) throws MbvException {
		log.info("-------------同步接口查询价格开始---------------");
		ResultBean resultBean = priceQueryHandler(bean,"sync");
		log.info("-------------同步接口查询价格结束---------------");
		return resultBean;
	}
	/**
	 * 价格运算逻辑返回保底规则
	 * @param priceProtectedBean 保底单号
	 * @param logEntity 日志
	 * @param bean 查询条件
	 * @return
	 */
	private Map<String, Object> calculateProtectPrice(ProtectedSettlementEntity priceProtectedBean, PriceAdjustConditionBean bean, PriceQueryLogsEntity logEntity) {
		Map<String, Object> protectedMap=new HashMap<String, Object>();
	/*	String protectedNum=(String) protectedMap.get("protectedNum");//保底单号
		protectedPrice=(Double)protectedMap.get("protectedPrice");//保底结算价
		retailPrice=(Double)protectedMap.get("retailPrice");//保底零售价*/	
		double retailPrice = bean.getRetailPrice();//零售价格
		double salePrice = bean.getSalePrice();//吊牌价
		String searchGoodSku=bean.getGoodSku();//查询的商品编码
		BigDecimal salePriceDec = new BigDecimal(salePrice);
		double protectedRetailPrice=0;//保底零售价
		double protectedPrice=0;//保底结算价
		double settlementValue=0;//保底设置的值
		String logMsg="开始计算保底规则";
		log.info(logMsg);
		protectedMap.put("protectedNum", priceProtectedBean.getSettlementNum());
		String  protectedDimension = priceProtectedBean.getOperationDimension();//保底维度
		String settlementType="";//保底规则
		if("seller_dimension".equals(protectedDimension)||"brand_dimension".equals(protectedDimension)){
			//商家或者品牌维度只有销售折率的方式
			settlementValue = priceProtectedBean.getSettlementValue();
			settlementType=priceProtectedBean.getSettlementType();
			BigDecimal settlementValueDec = new BigDecimal(settlementValue);
			//计算销售折率
			settlementValueDec=settlementValueDec.multiply(new BigDecimal("0.01"));
			//计算保底零售价
			protectedRetailPrice=salePriceDec.multiply(settlementValueDec).doubleValue();
			logMsg="保底维度为："+dimensionDesc.get(protectedDimension)+" 销售折率保底,销售折率为:"+settlementValueDec.doubleValue()+" 零售价为:"+retailPrice+" 保底零售价为:"+protectedRetailPrice;
			log.info(logMsg);
			setLogSuccessInfo(logEntity,logMsg);
			//保底零售价与零售价比较
			if(retailPrice>=protectedRetailPrice){
				protectedRetailPrice=retailPrice;
				logMsg="零售价大于等于保底零售价,使用零售价计算";
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else{
				logMsg="零售价小于保底零售价,使用保底零售价计算";
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}
			protectedMap.put("retailPrice", protectedRetailPrice);
			protectedMap.put("protectedPrice", protectedPrice);
		}else if("code_dimension".equals(protectedDimension)||"sku_dimension".equals(protectedDimension)){
			//保底维度为商家编码或者商家code维度,需要从商品信息上面获取保底规则和规则的值
			ProtectedSettlementProdEntity goodsEntity = new ProtectedSettlementProdEntity();
			if("code_dimension".equals(protectedDimension)){
				goodsEntity.setProdClsNum(searchGoodSku.substring(0, 6));
				goodsEntity.setSettlementId(priceProtectedBean.getId());
				goodsEntity=priceProtectedQueryService.queryGoods(goodsEntity);
			}else if("sku_dimension".equals(protectedDimension)){
				goodsEntity.setProdNum(searchGoodSku);
				goodsEntity.setSettlementId(priceProtectedBean.getId());
				goodsEntity=priceProtectedQueryService.queryGoods(goodsEntity);
			}
			settlementType = goodsEntity.getSettlementType();//保底规则
			settlementValue =goodsEntity.getSettlementValue();//保底值
			if("sale_rate".equals(settlementType)){
				//销售折率保底
				BigDecimal settlementValueDec = new BigDecimal(settlementValue);
				//计算销售折率
				settlementValueDec=settlementValueDec.multiply(new BigDecimal("0.01"));
				//计算保底零售价
				protectedRetailPrice=salePriceDec.multiply(settlementValueDec).doubleValue();
				logMsg="保底维度为："+dimensionDesc.get(protectedDimension)+" 销售折率保底,销售折率为:"+settlementValueDec.doubleValue()+" 零售价为:"+retailPrice+" 保底零售价为:"+protectedRetailPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
				//保底零售价与零售价比较
				if(retailPrice>=protectedRetailPrice){
					protectedRetailPrice=retailPrice;
					logMsg="零售价大于等于保底零售价,使用零售价计算";
					log.info(logMsg);
					setLogSuccessInfo(logEntity,logMsg);
				}else{
					logMsg="零售价小于保底零售价,使用保底零售价计算";
					log.info(logMsg);
					setLogSuccessInfo(logEntity,logMsg);
				}
				protectedMap.put("retailPrice", protectedRetailPrice);
				protectedMap.put("protectedPrice", protectedPrice);
			}else if("retail_price".equals(settlementType)){
				//零售价保底
				//计算保底零售价
				protectedRetailPrice=settlementValue;
				logMsg="保底维度为："+dimensionDesc.get(protectedDimension)+" 零售价保底,零售价为:"+retailPrice+" 保底零售价为:"+protectedRetailPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
				//保底零售价与零售价比较
				if(retailPrice>=protectedRetailPrice){
					protectedRetailPrice=retailPrice;
					logMsg="零售价大于等于保底零售价,使用零售价计算";
					log.info(logMsg);
					setLogSuccessInfo(logEntity,logMsg);
				}else{
					logMsg="零售价小于保底零售价,使用保底零售价计算";
					log.info(logMsg);
					setLogSuccessInfo(logEntity,logMsg);
				}
				protectedMap.put("retailPrice", protectedRetailPrice);
				protectedMap.put("protectedPrice", protectedPrice);
			}else if("purchase_price".equals(settlementType)){
				//采购结算保底
				protectedPrice=settlementValue;
				protectedMap.put("retailPrice",retailPrice);//零售价采用输入的零售价
				protectedMap.put("protectedPrice", protectedPrice);
				logMsg="保底维度为："+dimensionDesc.get(protectedDimension)+" 采购结算价保底,零售价为:"+retailPrice+" 保底结算价为:"+protectedPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else{
				throw new MbvException("保底规则不正确！");
			}
		}
		return protectedMap;
	}
	/* 
	 * 
	 * 价格运算逻辑计算最终结算价
	 */
	/**
	 * @param vpPriceAdjustBean 价格单
	 * @param bean 查询条件
	 * @param method 计算方法
	 * @param logEntity 日志
	 * @param protectedMap 保底设置
	 * @return
	 * @throws MbvException
	 */
	private double calculateFinalPrice(VpPriceAdjustBean vpPriceAdjustBean,PriceAdjustConditionBean bean,PriceQueryLogsEntity logEntity,Map<String, Object> protectedMap)throws MbvException {
		String settlementType = vpPriceAdjustBean.getSettlementType();//结算类型
		double salePrice = bean.getSalePrice();//吊牌价格
		double retailPrice = bean.getRetailPrice();//零售价格
		double protectedPrice=0;
		boolean isFixMoney=false;//判断是否是固定金额 false否  true是
		String logMsg="";
		if(protectedMap!=null){
			//保底设置不为空
			String protectedNum=(String) protectedMap.get("protectedNum");//保底单号
			protectedPrice=(Double)protectedMap.get("protectedPrice");//保底结算价
			retailPrice=(Double)protectedMap.get("retailPrice");//保底零售价
			logMsg="开始根据价格单："+vpPriceAdjustBean.getAdjustNum()+" 保底单:"+protectedNum+" 零售价格："+retailPrice+"计算商品结算价!";
		}else{
			logMsg="开始根据价格单："+vpPriceAdjustBean.getAdjustNum()+" 零售价格："+retailPrice+"计算商品结算价!";
		}
		log.info(logMsg);
		setLogSuccessInfo(logEntity,logMsg);
		double returnPrice = 0;//商家结算价格
		BigDecimal saleDecimal = new BigDecimal(String.valueOf(salePrice));//吊牌价decimal
		BigDecimal retailDecimal = new BigDecimal(String.valueOf(retailPrice));//零售价decimal
		BigDecimal saleRateDecimal = retailDecimal.divide(saleDecimal, MbvConstant.SCALE, BigDecimal.ROUND_HALF_UP);//销售折率
		logMsg="结算类型为:"+settlementTypeDesc.get(settlementType)+" 吊牌价为:"+salePrice+" 零售价格为:"+retailPrice;
		log.info(logMsg);
		setLogSuccessInfo(logEntity,logMsg);
		if("fixed_point".equals(settlementType)){
			//固定扣点
			String priceChoice = vpPriceAdjustBean.getPriceChoice();
			double sellerRate = vpPriceAdjustBean.getSellerRate();
			BigDecimal sellerRateDec=new BigDecimal(sellerRate);
			sellerRateDec=sellerRateDec.multiply(new BigDecimal("0.01"));
			logMsg="固定扣点,商家分成比率为:"+sellerRateDec.doubleValue();
			log.info(logMsg);
			setLogSuccessInfo(logEntity,logMsg);
			if("retail_price".equals(priceChoice)){
				//零售价
				returnPrice = retailDecimal.multiply(sellerRateDec).doubleValue();
				logMsg="零售价结算，计算公式:零售价*商家分成"+retailPrice+"*"+sellerRateDec.doubleValue()+"="+returnPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
				
			}else if("sale_price".equals(priceChoice)){
				//吊牌价
				returnPrice = saleDecimal.multiply(sellerRateDec).doubleValue();
				logMsg="吊牌价结算，计算公式:吊牌价*商家分成"+salePrice+"*"+sellerRateDec.doubleValue()+"="+returnPrice;
				isFixMoney=true;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}
			
		}else if("step_point".equals(settlementType)){
			//阶梯分成
			double saleRate = saleRateDecimal.doubleValue();
			logMsg="阶梯分成,销售折率为:"+saleRate;
			log.info(logMsg);
			setLogSuccessInfo(logEntity,logMsg);
			//获取阶梯分成规则
			VpPriceAdjustSettlementRulesEntity queryRule=null;
			List<VpPriceAdjustSettlementRulesEntity> rules = priceAdjustQueryService.selectRulesById(vpPriceAdjustBean.getId());
			for (VpPriceAdjustSettlementRulesEntity rule : rules) {
				double startRate = rule.getStartRate();
				double endRate = rule.getEndRate();
				BigDecimal startRateDec=new BigDecimal(startRate);
				BigDecimal endRateDec=new BigDecimal(endRate);
				startRate=startRateDec.multiply(new BigDecimal("0.01")).doubleValue();
				endRate=endRateDec.multiply(new BigDecimal("0.01")).doubleValue();
				//判断销售折率如果为100%则落到最后一个折率区间
				if(saleRate==1){
					//取100%折率区间
					if(endRate==1){
						logMsg="找到折率所在的区间：("+startRate+","+endRate+")";
						log.info(logMsg);
						setLogSuccessInfo(logEntity,logMsg);
						queryRule=rule;
						break;
					}
				}else{
					if(saleRate>=startRate&&saleRate<endRate){
						logMsg="找到折率所在的区间：("+startRate+","+endRate+")";
						log.info(logMsg);
						setLogSuccessInfo(logEntity,logMsg);
						queryRule=rule;
						break;
					}
				}
			}
			//根据折率所在的区间计算结算价格
			if(queryRule==null){
				throw new MbvException("没有找到折率所在的区间！");
			}
			String priceChoice = queryRule.getPriceChoice();//获取选择价格
			if("retail_price".equals(priceChoice)){
				//零售价结算
				double sellerRate =queryRule.getSellerRate();//获取商家分成比
				BigDecimal sellerRateDecimal = new BigDecimal(sellerRate);
				sellerRateDecimal=sellerRateDecimal.multiply(new BigDecimal("0.01"));
				logMsg="阶梯分成,商家分成比率为:"+sellerRateDecimal.doubleValue();
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
				returnPrice = retailDecimal.multiply(sellerRateDecimal).doubleValue();
				logMsg="零售价结算，计算公式:零售价*商家分成"+retailPrice+"*"+sellerRateDecimal.doubleValue()+"="+returnPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else if("sale_price".equals(priceChoice)){
				//吊牌价结算
				double sellerRate =queryRule.getSellerRate();//获取商家分成比
				BigDecimal sellerRateDecimal = new BigDecimal(sellerRate);
				sellerRateDecimal=sellerRateDecimal.multiply(new BigDecimal("0.01"));
				logMsg="阶梯分成,商家分成比率为:"+sellerRateDecimal.doubleValue();
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
				returnPrice = saleDecimal.multiply(sellerRateDecimal).doubleValue();
				logMsg="吊牌价结算，计算公式:吊牌价*商家分成"+salePrice+"*"+sellerRateDecimal.doubleValue()+"="+returnPrice;
				isFixMoney=true;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else if("cash".equals(priceChoice)){
				//金额
			    returnPrice=queryRule.getSpecifiedMoney();
			    isFixMoney=true;
			    logMsg="固定金额,结算价为"+returnPrice;
			    log.info("logMsg");
				setLogSuccessInfo(logEntity,logMsg);
			}else{
				throw new MbvException("无效的选择价格！");
			}
			}else if("specified_price".equals(settlementType)){
				//指定价格
				//查询商品信息列表
				VpPriceAdjustGoodsEntity goodsEntity = new VpPriceAdjustGoodsEntity();
				//判断价格单维度
				String operationDimension = vpPriceAdjustBean.getOperationDimension();
				String pordNum = bean.getGoodSku();
				if("sku_dimension".equals(operationDimension)){
					//如果是商品编码维度
					goodsEntity.setProdNum(pordNum);
				}else{
					goodsEntity.setProdClsNum(pordNum.substring(0, 6));
					
				}
				goodsEntity.setAdjustId(vpPriceAdjustBean.getId());
				goodsEntity=priceAdjustQueryService.selectBySelective(goodsEntity);
				if(goodsEntity==null){
					log.info("结算单号："+vpPriceAdjustBean.getAdjustNum()+"商品编码:"+pordNum+"无商品信息");
					throw new MbvException("结算单号："+vpPriceAdjustBean.getAdjustNum()+"商品编码:"+pordNum+"无商品信息");
				}
			    returnPrice=goodsEntity.getSpecifiedMoney();
			    isFixMoney=true;
			    logMsg="指定价格,结算价为"+returnPrice;
			    log.info("logMsg");
				setLogSuccessInfo(logEntity,logMsg);
			
		}else{
			throw new MbvException("无效的结算类型！");
		}
		logMsg="价格单计算的价格为:"+returnPrice;
		log.info(logMsg);
		setLogSuccessInfo(logEntity,logMsg);
		logMsg="开始保底价判断:";
		log.info(logMsg);
		if(isFixMoney){
			logMsg="是固定金额,用商家结算价,返回结算价为:"+returnPrice;
			log.info(logMsg);
			setLogSuccessInfo(logEntity,logMsg);
		}else{
			//判断保底结算价是否为0
			if(protectedPrice==0){
				logMsg="保底结算价为0,用商家结算价,返回结算价为:"+returnPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else if(returnPrice>=protectedPrice){
				logMsg="商家结算价大于保底结算价,返回结算价为:"+returnPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}else if(returnPrice<protectedPrice){
				returnPrice=protectedPrice;
				logMsg="商家结算价小于保底结算价,返回结算价为:"+returnPrice;
				log.info(logMsg);
				setLogSuccessInfo(logEntity,logMsg);
			}
		}
		return returnPrice;
	}
	
	/**
	 * 成功日志
	 * @param logEntity
	 * @param msg
	 */
	private void setLogSuccessInfo(PriceQueryLogsEntity logEntity,String msg) {
		PriceQueryLogsEntity queryLogsEntity =new PriceQueryLogsEntity();
		queryLogsEntity.setReqStatus("1");
		queryLogsEntity.setLogInfo(msg);
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
		queryLogsEntity.setSaleDate(logEntity.getSaleDate());
		/*queryLogsEntity.setProtectedNum(logEntity.getProtectedNum());
		queryLogsEntity.setAdjustNum(logEntity.getAdjustNum());
		queryLogsEntity.setReturnPrice(logEntity.getReturnPrice());*/
		queryLogsEntity.setLogEndFlag("0");
		//将日志对象加入list
		queryLogList.add(queryLogsEntity);
		
		/*//将日志放到日志队列中
		priceQueryMQService.pushLog(logEntity);*/
	}
	
}
