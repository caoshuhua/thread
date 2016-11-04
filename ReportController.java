package com.mbv.web.rest.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import com.alibaba.fastjson.JSONObject;
import com.mbv.biz.config.bean.ChartBean;
import com.mbv.biz.config.bean.CodeBean;
import com.mbv.biz.config.bean.DegBean;
import com.mbv.biz.config.bean.DegDtlBean;
import com.mbv.biz.config.bean.OrderBean;
import com.mbv.biz.config.bean.VenRnkBean;
import com.mbv.biz.config.entity.CodeDtlEntity;
import com.mbv.biz.config.entity.DegEntity;
import com.mbv.biz.config.entity.DegModifyRecordsEntity;
import com.mbv.biz.config.entity.UserEntity;
import com.mbv.biz.config.entity.VenRnkEntity;
import com.mbv.biz.config.service.CodeService;
import com.mbv.biz.config.service.OrderGoodsInfoService;
import com.mbv.biz.config.service.ReportService;
import com.mbv.common.constant.MbvConstant;
import com.mbv.common.exception.MbvException;
import com.mbv.web.rest.util.CommonConfig;
import com.mbv.web.rest.util.DateUtils;
import com.mbv.web.rest.util.DownloadCallable;
import com.mbv.web.rest.util.ExcelUtils;
import com.mbv.web.rest.vo.JqGridBaseEntityVo;
import com.mbv.web.rest.vo.VenRnkVo;
import com.metersbonwe.oms.api.bean.OrderGoodsInfo;

/***
 * 报表控制器
 * @author henry
 *
 */
@Controller
@RequestMapping("/report")
public class ReportController extends BaseController{
	
	private  Logger log = LoggerFactory.getLogger(ReportController.class);
	
	private static final DateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
	
	// 分页查询的分页长度。
	public static final int Bill_Query_PAGENATION_LENGTH = 5;
	
	public static final int PER_PAGE = 500;
	
	@Resource
	ReportService reportService;
	
	
	@Resource
	CodeService codeService;
	
	@Autowired
	CommonConfig config;
	
	@Resource
	OrderGoodsInfoService orderGoodsInfoService;
	/**
	 * 订单列表查询
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/queryOrderListFirst")
    public String queryByParamsFirst(Model model,HttpServletRequest request,HttpServletResponse response) throws MbvException{
		String status = request.getParameter("status");
		String docCode = request.getParameter("docCode");
		String consignee = request.getParameter("consignee");
		String currentPage = request.getParameter("currentPage");
		
		try{
			HttpSession session = request.getSession();  
	        String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
			
			String weekFrom = FORMATTER.format(new Date());
			String weekTo = FORMATTER.format(new Date());
			
			log.info("ReportController.queryByParams -> weekFrom: " + weekFrom + ", weekTo:" + weekTo);
			
			if (currentPage == null || currentPage.isEmpty()) {
				currentPage = "1";
			}
			int firstPage = Bill_Query_PAGENATION_LENGTH
					* (Integer.parseInt(currentPage) - 1);
			
			DegBean bean = new DegBean();
			bean.setDocCode(docCode);
			bean.setConsignee(consignee);
			bean.setDocState(status);
			bean.setUnitCode(unitCode);
			bean.setFirstPage(firstPage);
			bean.setLength(Bill_Query_PAGENATION_LENGTH);
			
			//date
			bean.setWeekFrom(weekFrom);
			bean.setWeekTo(weekTo);

			List<OrderBean> orderList = reportService.queryOrderByParams(bean);
			int totalItems = reportService.queryByParamsCount(bean);
			DegBean total = reportService.queryByParamsTotal(bean);
			int totalPage = totalItems
					% Bill_Query_PAGENATION_LENGTH == 0 ? totalItems
					/ Bill_Query_PAGENATION_LENGTH
					: totalItems / Bill_Query_PAGENATION_LENGTH
							+ 1;
			totalPage = totalPage < 1 ? 1 : totalPage;
			// 将查询条件值传到前台
			model.addAttribute("currentPage", currentPage);
			model.addAttribute("pageSize", Bill_Query_PAGENATION_LENGTH);
			model.addAttribute("totalPage", totalPage);
			model.addAttribute("totalItems", totalItems);
			model.addAttribute("orderList", orderList);
			model.addAttribute("weekFrom", weekFrom);
			model.addAttribute("weekTo", weekTo);
			
			//页面金额
			double totalTtlVal = 0.0;
			if(orderList != null && orderList.size() >0){
				for(OrderBean orderBean : orderList){
					totalTtlVal = totalTtlVal + orderBean.getDegBean().getTtlVal();
				}
			}
			model.addAttribute("totalTtlVal", totalTtlVal);
			if(total != null){
				model.addAttribute("total", total);
			}else{
				DegBean totalTmp = new DegBean();
				totalTmp.setTotalTtlQty(0);
				totalTmp.setTotalTtlVal(0);
				model.addAttribute("total", totalTmp);
			}
		}catch(MbvException me){
        	me.printStackTrace();
        	throw new MbvException(me.getMessage());
        }catch(RuntimeException re){
        	re.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }catch(Exception e){
        	e.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }
		
//        this.returnSuccess(response, map);
		request.setAttribute("activeMenu", "orderList");
        return "report/order_list";
    }
	
	/**
	 * 订单列表查询
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/queryOrderList")
    public String queryByParams(Model model,HttpServletRequest request,HttpServletResponse response) throws MbvException{
		String status = request.getParameter("status");
		String docCode = request.getParameter("docCode");
		String consignee = request.getParameter("consignee");
		String channelName = request.getParameter("channelName");
		String orderSn = request.getParameter("orderSn");
		String expressCode = request.getParameter("expressCode");
		
		String prodNum = request.getParameter("prodNum");
		String prodSku = request.getParameter("prodSku");
		String intelCode = request.getParameter("intelCode");
	    
		String currentPage = request.getParameter("currentPage");
		
		log.info("ReportController.queryOrderList -> status: " + status + ", docCode:" + docCode
				+ ",consignee:"+consignee+",channelName:"+channelName+",orderSn:"+orderSn+",prodNum:"+prodNum
				+",prodSku:"+prodSku+",intelCode:"+intelCode+",expressCode:"+expressCode);
		
		try{
			HttpSession session = request.getSession();  
	        String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
			
			String weekFrom = request.getParameter("weekFrom");
			String weekTo = request.getParameter("weekTo");
			
			log.info("ReportController.queryOrderList -> weekFrom: " + weekFrom + ", weekTo:" + weekTo);
			
			if (currentPage == null || currentPage.isEmpty()) {
				currentPage = "1";
			}
			int firstPage = Bill_Query_PAGENATION_LENGTH
					* (Integer.parseInt(currentPage) - 1);
			
			DegBean bean = new DegBean();
			bean.setDocCode(docCode);
			bean.setOrderSn(orderSn);
			bean.setConsignee(consignee);
			bean.setChannelName(channelName);
			bean.setDocState(status);
			bean.setUnitCode(unitCode);
			bean.setFirstPage(firstPage);
			
			bean.setBarcodeSysCode(prodNum);
			bean.setBarcodeCode(prodSku);
			bean.setIntlCode(intelCode);
			bean.setExpressCode(expressCode);
			bean.setLength(Bill_Query_PAGENATION_LENGTH);
			
			//date
			bean.setWeekFrom(weekFrom);
			bean.setWeekTo(weekTo);

			List<OrderBean> orderList = reportService.queryOrderByParams(bean);
			int totalItems = reportService.queryByParamsCount(bean);
			DegBean total = reportService.queryByParamsTotal(bean);
			int totalPage = totalItems
					% Bill_Query_PAGENATION_LENGTH == 0 ? totalItems
					/ Bill_Query_PAGENATION_LENGTH
					: totalItems / Bill_Query_PAGENATION_LENGTH
							+ 1;
			totalPage = totalPage < 1 ? 1 : totalPage;
			// 将查询条件值传到前台
			model.addAttribute("currentPage", currentPage);
			model.addAttribute("pageSize", Bill_Query_PAGENATION_LENGTH);
			model.addAttribute("totalPage", totalPage);
			model.addAttribute("totalItems", totalItems);
			model.addAttribute("orderList", orderList);
			model.addAttribute("status", status);
			model.addAttribute("docCode", docCode);
			model.addAttribute("orderSn", orderSn);
			model.addAttribute("channelName", channelName);
			model.addAttribute("consignee", consignee);
			model.addAttribute("weekFrom", weekFrom);
			model.addAttribute("weekTo", weekTo);
			model.addAttribute("expressCode", expressCode);
			
			model.addAttribute("prodNum", prodNum);
			model.addAttribute("prodSku", prodSku);
			model.addAttribute("intelCode", intelCode);
			
			//页面金额
			double totalTtlVal = 0.0;
			if(orderList != null && orderList.size() >0){
				for(OrderBean orderBean : orderList){
					totalTtlVal = totalTtlVal + orderBean.getDegBean().getTtlVal();
				}
			}
			model.addAttribute("totalTtlVal", totalTtlVal);
			
			if(total != null){
				model.addAttribute("total", total);
			}else{
				DegBean totalTmp = new DegBean();
				totalTmp.setTotalTtlQty(0);
				totalTmp.setTotalTtlVal(0);
				model.addAttribute("total", totalTmp);
			}
		}catch(MbvException me){
        	me.printStackTrace();
        	throw new MbvException(me.getMessage());
        }catch(RuntimeException re){
        	re.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }catch(Exception e){
        	e.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }
		
		request.setAttribute("activeMenu", "orderList");
        return "report/order_list";
    }
	
	/**
	 * 查询订单明细
	 * @param request
	 * @param response
	 * @return
	 * @throws MbvException
	 */
	@RequestMapping(value="/queryOrderDetail")
    public String queryOrderDetail(Model model,HttpServletRequest request,HttpServletResponse response) throws MbvException{
      //先在客户端验证用户以及它的手机号码
    	try{
    		HttpSession session = request.getSession();  
            String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
        	
        	String id = request.getParameter("degId");
        	String detailLable = request.getParameter("from");
        	
        	log.info("ReportController.queryOrderDetail -> id: " + id);
        	
        	OrderBean orderBean = null;
        	
        	DegBean bean = new DegBean();
        	bean.setId(Long.valueOf(id));
        	bean.setUnitCode(unitCode);
        	bean.setFirstPage(0);
        	bean.setLength(Bill_Query_PAGENATION_LENGTH);
        	
    		log.info("ReportController.queryOrderDetail -> paramsMap: " + bean);
    		
    		List<OrderBean> orderList = reportService.queryOrderByParamsDetail(bean);
    		
    		if(orderList != null && orderList.size() >0){
    			orderBean = orderList.get(0);
    			
    			log.info("ReportController.queryOrderDetail -> orderBean: " + orderBean);
    	    	
    	    	model.addAttribute("orderBean", orderBean);
    	    	model.addAttribute("docCode", orderBean.getDegBean().getDocCode());
    		}
    		model.addAttribute("detailLable", detailLable);
    	}catch(MbvException me){
        	me.printStackTrace();
        	throw new MbvException(me.getMessage());
        }catch(RuntimeException re){
        	re.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }catch(Exception e){
        	e.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }
    	return "/report/order_detail";
    }
	
	/**
	 * 根据订单号查询定制信息
	 * @param request
	 * @param response
	 * @return
	 * @throws MbvException
	 */
	@RequestMapping(value="/queryOrderGoodsDetail")
    public void queryOrderGoodsDetail(Model model,HttpServletRequest request,HttpServletResponse response) throws MbvException{
		try{
			String orderSn = request.getParameter("orderSn");
			String prodNum = request.getParameter("prodNum");
			String c2mItemStr="";//定制化信息json字符串
			String extensionCode="";//定制信息的类型（c2b或者c2m）
			Map<String, Object> map = new HashMap<String, Object>();
			//调用oms订单接口查询商品详细信息
			@SuppressWarnings("unchecked")
			List<OrderGoodsInfo> orderGoodsInfo= (List<OrderGoodsInfo>) orderGoodsInfoService.getOrderGoodsInfo(orderSn,prodNum);
			OrderGoodsInfo good = orderGoodsInfo.get(0);
			c2mItemStr=good.getC2mItemStr();
			extensionCode=good.getExtensionCode();
			map.put("c2mItemStr", c2mItemStr);
			map.put("extensionCode", extensionCode);
			returnSuccess(response, map);
		}catch(MbvException e){
			throw new MbvException(e.getMessage());
		}
		catch(Exception e){
			throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
		}
	}
	
	@RequestMapping(value = "findOrderById", method = RequestMethod.POST)
	@ResponseBody
	public void findOrderById(HttpServletRequest request, HttpServletResponse response) throws MbvException {
		// 判断vo
		Map<String, Object> map = new HashMap<String, Object>();
		try{
			String degId = request.getParameter("id");
			long id = Long.valueOf(degId);
			DegEntity entity = reportService.selectByPrimaryKey(id);
			log.info("ReportController.findOrderById -> id: " + id);
			if(entity != null){
				map.put("data", entity);
				map.put("success", true);
			}else{
				map.put("status","ERROR");
	        	map.put("reason", "系统忙，请联系管理员！");
			}
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
		// 保存成功
		returnSuccess(response, map);
	}
	
	@RequestMapping(value = "findExpressById", method = RequestMethod.POST)
	@ResponseBody
	public void findExpressById(HttpServletRequest request, HttpServletResponse response) throws MbvException {
		// 判断vo
		Map<String, Object> map = new HashMap<String, Object>();
		try{
			String degId = request.getParameter("id");
			long id = Long.valueOf(degId);
			DegEntity entity = reportService.selectByPrimaryKey(id);
			log.info("ReportController.findOrderById -> id: " + id);
			if(entity != null){
				List<CodeDtlEntity> list = codeService.selectCodeDtlBySysCode("SHIPPING_CODE");
				map.put("shipList", list);
				
				map.put("data", entity);
				map.put("success", true);
			}else{
				map.put("status","ERROR");
	        	map.put("reason", "系统忙，请联系管理员！");
			}
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
		// 保存成功
		returnSuccess(response, map);
	}
	
	/**
	 * 添加备注信息
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/modifyRemark",method=RequestMethod.POST)
    public void modifyRemark(HttpServletRequest request,HttpServletResponse response) throws MbvException{
		boolean flag = true;
		String content = request.getParameter("content");
		String id = request.getParameter("docCode");
		log.info("ReportController.modifyRemark -> content: " + content +" id: " + id);

		Map<String,Object> map = new HashMap<String,Object>();
		try{
//			DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
//	        String today = sdf.format(new Date());
	        
	        HttpSession session = request.getSession();  
	        String userCode = (String) session.getAttribute("USER_CODE");  
	        
	        if(StringUtils.isNotEmpty(content)){
	        	DegBean bean = new DegBean();
	        	bean.setId(Long.valueOf(id));
	        	bean.setRemark(content);
	        	bean.setLastModifiedUser(userCode);
	        	bean.setLastModifiedDate(new Date());
	        	
//	        	Map<String, Object> paramsMap = new HashMap<String, Object>();
//	    		paramsMap.put("id", id);
//	    		paramsMap.put("remark", content);
//	    		paramsMap.put("lastModifiedUser", userCode);
//	    		paramsMap.put("lastModifiedDate", today);
	    		flag = reportService.updateRemark(bean);
	        }
	        
	        if(flag == true){
	        	map.put("success", true);
	            this.returnSuccess(response, map);
	        }else{
	        	map.put("status","ERROR");
	        	map.put("reason", "系统忙，请联系管理员！");
	        	this.returnSuccess(response, map);
	        }
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
    }
	
	/**
	 * 修改快递
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/modifyExpress",method=RequestMethod.POST)
    public void modifyExpress(HttpServletRequest request,HttpServletResponse response) throws MbvException{
		boolean flag = true;
		String expressCode = request.getParameter("expressCode");
		String tspComCode = request.getParameter("tspComCode");
		String id = request.getParameter("docCode");
		log.info("ReportController.modifyExpress -> expressCode: " + expressCode+",tspComCode:"+tspComCode +" id: " + id);

		Map<String,Object> map = new HashMap<String,Object>();
		try{
	        HttpSession session = request.getSession();  
	        String userCode = (String) session.getAttribute("USER_CODE");  
	        
	        if(StringUtils.isNotEmpty(expressCode) && StringUtils.isNotEmpty(tspComCode)){
	        	DegBean bean = new DegBean();
	        	bean.setId(Long.valueOf(id));
	        	bean.setExpressCode(expressCode);
	        	bean.setTspComCode(tspComCode);
	        	bean.setLastModifiedUser(userCode);
	        	bean.setLastModifiedDate(new Date());
	    		flag = reportService.updateOrderInfo(bean);
	        }
	        
	        if(flag == true){
	        	map.put("success", true);
	            this.returnSuccess(response, map);
	        }else{
	        	map.put("status","ERROR");
	        	map.put("reason", "系统忙，请联系管理员！");
	        	this.returnSuccess(response, map);
	        }
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
    }
	
	/**
	 * 查询订单变更记录
	 * @param request
	 * @param response
	 * @return
	 * @throws MbvException
	 */
	@RequestMapping(value="/queryOrderModifyDetail")
    public ModelAndView queryOrderModifyDetail(HttpServletRequest request,HttpServletResponse response) throws MbvException{
      //先在客户端验证用户以及它的手机号码
    	ModelAndView mav = new ModelAndView();
    	
    	try{
    		HttpSession session = request.getSession();  
            String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
        	
        	String id = request.getParameter("codeId");
        	
        	log.info("ReportController.queryOrderModifyDetail -> id: " + id);
        	
        	OrderBean orderBean = null;
//    		OrderBean orderBean = reportService.queryOrderByCode(docCode);
        	
        	DegBean bean = new DegBean();
        	bean.setId(Long.valueOf(id));
        	bean.setUnitCode(unitCode);
        	bean.setFirstPage(0);
        	bean.setLength(Bill_Query_PAGENATION_LENGTH);
        	
    		List<OrderBean> orderList = reportService.queryOrderByParams(bean);
    		
    		List<DegModifyRecordsEntity> degModifyRecordsList = new ArrayList<DegModifyRecordsEntity>();
    		
    		if(orderList != null && orderList.size() >0){
    			orderBean = orderList.get(0);
    			
    			degModifyRecordsList = reportService.queryDegModifyRecordsByCode(orderBean.getDegBean().getDocCode());
    			
    			log.info("ReportController.queryOrderModifyDetail -> degModifyRecordsList: " + degModifyRecordsList);
    			
    			mav.addObject("orderBean", orderBean);
    	    	mav.addObject("docCode", orderBean.getDegBean().getDocCode());
    	    	
    	    	mav.addObject("degModifyRecordsList", degModifyRecordsList);
    	        mav.setViewName("/order/order_modify_detail"); 
    		}
    	}catch(MbvException me){
        	me.printStackTrace();
        	throw new MbvException(me.getMessage());
        }catch(RuntimeException re){
        	re.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }catch(Exception e){
        	e.printStackTrace();
        	throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
        }
    	
		return mav;
    }
	
	/**
	 * 根据发货单编号查询订单变更记录
	 * @param u
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/findOrderModifyDetail",method=RequestMethod.POST)
    public void findOrderModifyDetail(UserEntity u,HttpServletRequest request,HttpServletResponse response) throws MbvException{
		Map<String,Object> map = new HashMap<String,Object>();
		
		String docCode = request.getParameter("docCode");
		try{
			log.info("ReportController.existsOrderModifyDetail -> docCode: " + docCode);
			
	        List<DegModifyRecordsEntity> degModifyRecordsList = reportService.queryDegModifyRecordsByCode(docCode);
	        if(degModifyRecordsList != null && degModifyRecordsList.size() > 0){
	        	log.info("existsOrderModifyDetail->degModifyRecordsList.size():"+degModifyRecordsList.size());	
	        	map.put("list",degModifyRecordsList);
	        	this.returnSuccess(response, map);
	        }else{
	        	map.put("status","ERROR");
	        	map.put("reason", "不存在订单变更记录！");
	            this.returnSuccess(response, map);
	        }
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
    }
	
	/**
	 * 是否存在订单信息变更记录
	 * @param u
	 * @param request
	 * @param response
	 * @throws MbvException
	 */
	@RequestMapping(value="/existsOrderModifyDetail",method=RequestMethod.POST)
    public void existsOrderModifyDetail(UserEntity u,HttpServletRequest request,HttpServletResponse response) throws MbvException{
		Map<String,Object> map = new HashMap<String,Object>();
		
		String docCode = request.getParameter("docCode");
		try{
			log.info("ReportController.existsOrderModifyDetail -> docCode: " + docCode);
			
	        List<DegModifyRecordsEntity> degModifyRecordsList = reportService.queryDegModifyRecordsByCode(docCode);
	        if(degModifyRecordsList != null && degModifyRecordsList.size() > 0){
	        	log.info("existsOrderModifyDetail->degModifyRecordsList.size():"+degModifyRecordsList.size());	
	        	this.returnSuccess(response, map);
	        }else{
	        	map.put("status","ERROR");
	        	map.put("reason", "不存在订单变更记录！");
	            this.returnSuccess(response, map);
	        }
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
    }
	
	@RequestMapping(value="/download")
    public String download(HttpServletRequest request,HttpServletResponse response) throws MbvException, Exception{
		try{
			String status = request.getParameter("status");
			String docCode = request.getParameter("docCode");
			String consignee = request.getParameter("consignee");
			String orderSn = request.getParameter("orderSn");
			String prodNum = request.getParameter("prodNum");
			String prodSku = request.getParameter("prodSku");
			String intelCode = request.getParameter("intelCode");
			String channelName = request.getParameter("channelName");
			
			
			HttpSession session = request.getSession();  
	        String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
			
			String weekFrom = request.getParameter("weekFrom");
			String weekTo = request.getParameter("weekTo");
			
			log.info("ReportController.queryByParams -> weekFrom: " + weekFrom + ", weekTo:" + weekTo);
			
			DegBean bean = new DegBean();
			bean.setDocCode(docCode);
			bean.setConsignee(consignee);
			bean.setDocState(status);
			bean.setChannelName(channelName);
			bean.setUnitCode(unitCode);
			
			bean.setOrderSn(orderSn);
			bean.setBarcodeSysCode(prodNum);
			bean.setBarcodeCode(prodSku);
			bean.setIntlCode(intelCode);
			
			//date
			bean.setWeekFrom(weekFrom);
			bean.setWeekTo(weekTo);
			
			Long beginTime1 = System.currentTimeMillis();
			List<OrderBean> orderList = reportService.queryOrderByParamsForDownload(bean);
			Long endTime1 = System.currentTimeMillis();
			log.info("queryOrderByParamsForDownload 总共：" + (endTime1 - beginTime1) / 1000 + "秒");// 计算时间
			
			Long beginTime2 = System.currentTimeMillis();
			if(orderList != null && orderList.size() > 0){
				SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd"); 
			    String strDate = formatter.format(new Date()); 
			    
				String fileName="order-list-"+strDate;
		        //填充projects数据
		        List<Map<String,Object>> list = createExcelRecord(orderList);
//		        String columnNames[]={"发货单编号","OS订单号","发货单状态","物流承运商","商品编号","商品名称","商品价格","商品数量"};//列名
//		        String keys[]   =    {"docCode","orderSn","docState","tspComCode","prodNum","productName","unitPrice","qty"};//map中的key
		        
		        String columnNames[]={"发货单编号","OS订单编号","商品编码","商家货号（SKU）","品牌名称","商品信息","商品数量","付款时间","交易价","订单金额","发货单状态","下单人","收货人","收货地址","邮编","手机号码","订单备注","商家备注","订单来源","销售渠道"};//列名
 		        String keys[]   =    {"docCode","orderSn","prodNum","barcodeCode","brandName","prodInfo","qty","payTime","unitPrice","ttlVal","docState","custName","consignee","delivAddress","delivPstd","moblie","orderRemark","remark","orderFrom","channelName"};//map中的key
 		        
		        ByteArrayOutputStream os = new ByteArrayOutputStream();
		        try {
		        	ExcelUtils.createWorkBook(list,keys,columnNames).write(os);
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
		        byte[] content = os.toByteArray();
		        InputStream is = new ByteArrayInputStream(content);
		        // 设置response参数，可以打开下载页面
		        response.reset();
		        response.setContentType("application/vnd.ms-excel;charset=utf-8");
		        response.setHeader("Content-Disposition", "attachment;filename="+ new String((fileName + ".xls").getBytes(), "iso-8859-1"));
		        ServletOutputStream out = response.getOutputStream();
		        BufferedInputStream bis = null;
		        BufferedOutputStream bos = null;
		        try {
		            bis = new BufferedInputStream(is);
		            bos = new BufferedOutputStream(out);
		            byte[] buff = new byte[2048];
		            int bytesRead;
		            // Simple read/write loop.
		            while (-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
		                bos.write(buff, 0, bytesRead);
		            }
		        } catch (final IOException e) {
		            throw e;
		        } finally {
		            if (bis != null)
		                bis.close();
		            if (bos != null)
		                bos.close();
		        }
			}
			Long endTime2 = System.currentTimeMillis();
			log.info("response excel 总共：" + (endTime2 - beginTime2) / 1000 + "秒");// 计算时间
		}catch(MbvException e){
			throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
		}catch(RuntimeException e){
			throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
		}
		return null;
	}
	
	@RequestMapping(value="/download2")
    public String download2(HttpServletRequest request,HttpServletResponse response) throws MbvException, Exception{
		try{
			String status = request.getParameter("status");
			String docCode = request.getParameter("docCode");
			String consignee = request.getParameter("consignee");
			String orderSn = request.getParameter("orderSn");
			String prodNum = request.getParameter("prodNum");
			String prodSku = request.getParameter("prodSku");
			String intelCode = request.getParameter("intelCode");
			String channelName = request.getParameter("channelName");
			
			
			HttpSession session = request.getSession();  
	        String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
			
			String weekFrom = request.getParameter("weekFrom");
			String weekTo = request.getParameter("weekTo");
			
			log.info("ReportController.queryByParams -> weekFrom: " + weekFrom + ", weekTo:" + weekTo);
			
			DegBean bean = new DegBean();
			bean.setDocCode(docCode);
			bean.setConsignee(consignee);
			bean.setDocState(status);
			bean.setChannelName(channelName);
			bean.setUnitCode(unitCode);
			
			bean.setOrderSn(orderSn);
			bean.setBarcodeSysCode(prodNum);
			bean.setBarcodeCode(prodSku);
			bean.setIntlCode(intelCode);
			
			//date
			bean.setWeekFrom(weekFrom);
			bean.setWeekTo(weekTo);
			
			Long beginTime1 = System.currentTimeMillis();
			int totalRecord = reportService.queryByParamsForDownloadPageCnt(bean);
			log.info("totalRecord:" + totalRecord);
			if(totalRecord >= PER_PAGE){
				int maxPage = totalRecord % PER_PAGE == 0 ? totalRecord / PER_PAGE : totalRecord / PER_PAGE + 1;
				// 创建一个线程池
				ExecutorService pool = Executors.newFixedThreadPool(maxPage);
				// 创建多个有返回值的任务
				List<Future> futureList = new ArrayList<Future>();
				for (int i = 0; i < maxPage; i++) {
					bean.setOffset((i-1)*PER_PAGE);
					bean.setRows(PER_PAGE);
				    Callable c = new DownloadCallable(i+"",reportService,bean);
				    // 执行任务并获取Future对象
				    Future f = pool.submit(c);
				    // System.out.println(">>>" + f.get().toString());
				    futureList.add(f);
				}
				// 关闭线程池
				pool.shutdown();
				Long endTime1 = System.currentTimeMillis();
				log.info("mul threads 总共：" + (endTime1 - beginTime1) / 1000 + "秒");// 计算时间
				
				Long beginTime2 = System.currentTimeMillis();
				// 获取所有并发任务的运行结果
				List<OrderBean> orderList = new ArrayList<OrderBean>();
				for (Future f : futureList) {
				    // 从Future对象上获取任务的返回值，并输出到控制台
				    List<OrderBean> tmpOrderList = (List<OrderBean>) f.get();
				    orderList.addAll(tmpOrderList);
				}   
//				for(int i = 1; i <= maxPage; i++){
//					bean.setOffset((i-1)*PER_PAGE);
//					bean.setRows(PER_PAGE);
//					List<OrderBean> tmpOrderList = reportService.queryOrderByParamsForDownloadPage(bean);
//					orderList.addAll(tmpOrderList);
//				}
				log.info("orderList.size():"+orderList.size());
				if(orderList != null && orderList.size() > 0){
					SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd"); 
				    String strDate = formatter.format(new Date()); 
				    
					String fileName="order-list-"+strDate;
			        //填充projects数据
			        List<Map<String,Object>> list = createExcelRecord(orderList);
//			        String columnNames[]={"发货单编号","OS订单号","发货单状态","物流承运商","商品编号","商品名称","商品价格","商品数量"};//列名
//			        String keys[]   =    {"docCode","orderSn","docState","tspComCode","prodNum","productName","unitPrice","qty"};//map中的key
			        
			        String columnNames[]={"发货单编号","OS订单编号","商品编码","商家货号（SKU）","品牌名称","商品信息","商品数量","付款时间","交易价","订单金额","发货单状态","下单人","收货人","收货地址","邮编","手机号码","订单备注","商家备注","订单来源","销售渠道"};//列名
	 		        String keys[]   =    {"docCode","orderSn","prodNum","barcodeCode","brandName","prodInfo","qty","payTime","unitPrice","ttlVal","docState","custName","consignee","delivAddress","delivPstd","moblie","orderRemark","remark","orderFrom","channelName"};//map中的key
	 		        
			        ByteArrayOutputStream os = new ByteArrayOutputStream();
			        try {
			        	ExcelUtils.createWorkBook(list,keys,columnNames).write(os);
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
			        byte[] content = os.toByteArray();
			        InputStream is = new ByteArrayInputStream(content);
			        // 设置response参数，可以打开下载页面
			        response.reset();
			        response.setContentType("application/vnd.ms-excel;charset=utf-8");
			        response.setHeader("Content-Disposition", "attachment;filename="+ new String((fileName + ".xls").getBytes(), "iso-8859-1"));
			        ServletOutputStream out = response.getOutputStream();
			        BufferedInputStream bis = null;
			        BufferedOutputStream bos = null;
			        try {
			            bis = new BufferedInputStream(is);
			            bos = new BufferedOutputStream(out);
			            byte[] buff = new byte[2048];
			            int bytesRead;
			            // Simple read/write loop.
			            while (-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
			                bos.write(buff, 0, bytesRead);
			            }
			        } catch (final IOException e) {
			            throw e;
			        } finally {
			            if (bis != null)
			                bis.close();
			            if (bos != null)
			                bos.close();
			        }
				}
				Long endTime2 = System.currentTimeMillis();
				log.info("response excel 总共：" + (endTime2 - beginTime2) / 1000 + "秒");// 计算时间
			}else{
				bean.setOffset(0);
				bean.setRows(totalRecord);
				List<OrderBean> orderList = reportService.queryOrderByParamsForDownloadPage(bean);
				
				if(orderList != null && orderList.size() > 0){
					SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd"); 
				    String strDate = formatter.format(new Date()); 
				    
					String fileName="order-list-"+strDate;
			        //填充projects数据
			        List<Map<String,Object>> list = createExcelRecord(orderList);
//			        String columnNames[]={"发货单编号","OS订单号","发货单状态","物流承运商","商品编号","商品名称","商品价格","商品数量"};//列名
//			        String keys[]   =    {"docCode","orderSn","docState","tspComCode","prodNum","productName","unitPrice","qty"};//map中的key
			        
			        String columnNames[]={"发货单编号","OS订单编号","商品编码","商家货号（SKU）","品牌名称","商品信息","商品数量","付款时间","交易价","订单金额","发货单状态","下单人","收货人","收货地址","邮编","手机号码","订单备注","商家备注","订单来源","销售渠道"};//列名
	 		        String keys[]   =    {"docCode","orderSn","prodNum","barcodeCode","brandName","prodInfo","qty","payTime","unitPrice","ttlVal","docState","custName","consignee","delivAddress","delivPstd","moblie","orderRemark","remark","orderFrom","channelName"};//map中的key
	 		        
			        ByteArrayOutputStream os = new ByteArrayOutputStream();
			        try {
			        	ExcelUtils.createWorkBook(list,keys,columnNames).write(os);
			        } catch (IOException e) {
			            e.printStackTrace();
			        }
			        byte[] content = os.toByteArray();
			        InputStream is = new ByteArrayInputStream(content);
			        // 设置response参数，可以打开下载页面
			        response.reset();
			        response.setContentType("application/vnd.ms-excel;charset=utf-8");
			        response.setHeader("Content-Disposition", "attachment;filename="+ new String((fileName + ".xls").getBytes(), "iso-8859-1"));
			        ServletOutputStream out = response.getOutputStream();
			        BufferedInputStream bis = null;
			        BufferedOutputStream bos = null;
			        try {
			            bis = new BufferedInputStream(is);
			            bos = new BufferedOutputStream(out);
			            byte[] buff = new byte[2048];
			            int bytesRead;
			            // Simple read/write loop.
			            while (-1 != (bytesRead = bis.read(buff, 0, buff.length))) {
			                bos.write(buff, 0, bytesRead);
			            }
			        } catch (final IOException e) {
			            throw e;
			        } finally {
			            if (bis != null)
			                bis.close();
			            if (bos != null)
			                bos.close();
			        }
				}
			}
		}catch(MbvException e){
			throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
		}catch(RuntimeException e){
			throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
		}
		return null;
	}
	
	public Map getDicMap(String sysCode) throws MbvException {
 	   try{
 		   Map<String,Object> dicMap = new HashMap<String,Object>();
 		   List<CodeDtlEntity> list = codeService.selectCodeDtlBySysCode(sysCode);
 		   for(CodeDtlEntity codeDtl : list) {
 			   dicMap.put(codeDtl.getCode(), codeDtl.getName());
 		   }
 		   return dicMap;
 	   }catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
	       	}catch(RuntimeException e){
	       		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
	       	}
    }
	
    private List<Map<String, Object>> createExcelRecord(List<OrderBean> beans) {
    	Map<String,Object> dicMap = this.getDicMap("CHANNEL_CODE");
    	SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    	
        List<Map<String, Object>> listmap = new ArrayList<Map<String, Object>>();
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("sheetName", "订单明细列表");
        listmap.add(map);
        OrderBean orderBean = null;
        for (int j = 0; j < beans.size(); j++) {
        	orderBean = beans.get(j);
        	List<DegDtlBean> degDtlBeanList = orderBean.getDegDtlBeanList();
        	if(degDtlBeanList != null && degDtlBeanList.size() > 0){
        		for(DegDtlBean dtl : degDtlBeanList){
                    Map<String, Object> mapValue = new HashMap<String, Object>();
             		mapValue.put("docCode", orderBean.getDegBean().getDocCode());
             		mapValue.put("orderSn", orderBean.getDegBean().getOrderSn());
             		mapValue.put("prodNum", dtl.getProdNum());
             		mapValue.put("barcodeCode", dtl.getBarcodeCode());
             		mapValue.put("brandName", dtl.getBrandName());
             		mapValue.put("prodInfo", dtl.getProductName()+ " "+ dtl.getColorName() +" "+ dtl.getSizeName());
             		mapValue.put("qty", new Double(dtl.getQty()).intValue());
             		mapValue.put("payTime", orderBean.getDegBean().getPayTime()==null?"":dateFormat.format(orderBean.getDegBean().getPayTime()));
             		mapValue.put("unitPrice", dtl.getUnitPrice());
             		mapValue.put("ttlVal", orderBean.getDegBean().getTtlVal());
             		mapValue.put("docState", orderBean.getDegBean().getDocState());
             		mapValue.put("custName", orderBean.getDegBean().getCustName());
             		mapValue.put("consignee", orderBean.getDegBean().getConsignee());
             		mapValue.put("delivAddress", orderBean.getDegBean().getProvince()+orderBean.getDegBean().getCity()+orderBean.getDegBean().getDistrict()+orderBean.getDegBean().getDelivAddress());
             		mapValue.put("delivPstd", orderBean.getDegBean().getDelivPstd());
             		mapValue.put("moblie", orderBean.getDegBean().getMoblie());
             		mapValue.put("orderRemark", orderBean.getDegBean().getOrderRemark());
             		mapValue.put("remark", orderBean.getDegBean().getRemark());
             		mapValue.put("orderFrom", "美邦商家后台");
             		String channelCode = orderBean.getDegBean().getChannelCode();
        			if(channelCode != null && !"".equals(channelCode)) {
        				mapValue.put("channelName",(String)(dicMap.get(channelCode)==null?"":dicMap.get(channelCode)));
        			}else{
        				mapValue.put("channelName","");
        			}
             		
             		listmap.add(mapValue);
        		}
        	}
        }
        return listmap;
    }
    
    @RequestMapping(value="/existsOrderDownload")
    public void existsOrderDownload(HttpServletRequest request,HttpServletResponse response) throws MbvException{
		Map<String,Object> map = new HashMap<String,Object>();
		try{
			String status = request.getParameter("status");
			String docCode = request.getParameter("docCode");
			String consignee = request.getParameter("consignee");
			String orderSn = request.getParameter("orderSn");
			String prodNum = request.getParameter("prodNum");
			String prodSku = request.getParameter("prodSku");
			String intelCode = request.getParameter("intelCode");
			HttpSession session = request.getSession();  
	        String unitCode = (String) session.getAttribute(MbvConstant.UNIT_CODE);  
			
			String weekFrom = request.getParameter("weekFrom");
			String weekTo = request.getParameter("weekTo");
			
			log.info("ReportController.existsOrderDownload -> weekFrom: " + weekFrom + ", weekTo:" + weekTo);
			
			DegBean bean = new DegBean();
			bean.setDocCode(docCode);
			bean.setConsignee(consignee);
			bean.setDocState(status);
			bean.setUnitCode(unitCode);
			
			bean.setOrderSn(orderSn);
			bean.setBarcodeSysCode(prodNum);
			bean.setBarcodeCode(prodSku);
			bean.setIntlCode(intelCode);
			
			//date
			bean.setWeekFrom(weekFrom);
			bean.setWeekTo(weekTo);
			
			log.info("ReportController.existsOrderDownload -> paramsMap: " + bean);
			
			int totalItems = reportService.queryByParamsCount(bean);
			
	        if(totalItems > 0){
	        	if(totalItems > 10000){
	        		map.put("status","ERROR");
		        	map.put("reason", "数据量超过1万条,请根据查询条件分批导出!");
		            this.returnSuccess(response, map);
	        	}else{
	        		log.info("ReportController.existsOrderDownload -> totalItems: " + totalItems);
		        	map.put("status","");
		        	this.returnSuccess(response, map);
	        	}
	        }else{
	        	map.put("status","ERROR");
	        	map.put("reason", "无任何数据");
	            this.returnSuccess(response, map);
	        }
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
    }
    
    
    @RequestMapping(value="/queryOrderTrackInfo")
    public void queryOrderTrackInfo(HttpServletRequest request,HttpServletResponse response) throws MbvException{
		Map<String,Object> map = new HashMap<String,Object>();
		try{
			String orderSn = request.getParameter("orderSn");
			String expressCode = request.getParameter("expressCode");
			String trackNo = request.getParameter("trackNo");
			log.info("ReportController.queryOrderTrackInfo -> orderSn: " + orderSn + ",expressCode: " + expressCode + ", trackNo:" + trackNo);
			
			map.put("status","ERROR");
        	map.put("reason", "无任何数据");
        	
        	Map<String, String> exressMap = new HashMap<String, String>();
        	List<CodeDtlEntity> list = codeService.selectCodeDtlBySysCode("SHIPPING_CODE");
        	if(list != null && list.size() > 0){
        		for(CodeDtlEntity dtl : list){
        			exressMap.put(dtl.getCode(), dtl.getDescription());
        		}
        	}
        	String des = exressMap.get(expressCode);
        	if(StringUtils.isEmpty(des)){
        		des = "http://www.kuaidi100.com/";
        	}
        	map.put("des", des);
            this.returnSuccess(response, map);
//            
//			OrderInfo orderInfo = HttpInvoker.readContentFromGet(config.getMbvHttp(), orderSn, expressCode, trackNo);
//			
//	        if(orderInfo != null && orderInfo.getExpress() != null && orderInfo.getExpress().size() > 0){
//	        	ExpressInfo expressInfo = orderInfo.getExpress().get(0);
//	        	List<ExpressContent> contentList = expressInfo.getData();
//	        	if(contentList != null && contentList.size() > 0){
//	        		Collections.reverse(contentList);
//	        		map.put("list", contentList);
//	        		map.put("status","");
//		        	this.returnSuccess(response, map);
//	        	}else{
//	        		map.put("status","ERROR");
//		        	map.put("reason", "无任何数据");
//		        	
//		        	Map<String, String> exressMap = new HashMap<String, String>();
//		        	List<CodeDtlEntity> list = codeService.selectCodeDtlBySysCode("SHIPPING_CODE");
//		        	if(list != null && list.size() > 0){
//		        		for(CodeDtlEntity dtl : list){
//		        			exressMap.put(dtl.getCode(), dtl.getDescription());
//		        		}
//		        	}
//		        	String des = exressMap.get(expressCode);
//		        	if(StringUtils.isEmpty(des)){
//		        		des = "http://www.kuaidi100.com/";
//		        	}
//		        	map.put("des", des);
//		            this.returnSuccess(response, map);
//	        	}
//	        }else{
//	        	map.put("status","ERROR");
//	        	map.put("reason", "无任何数据");
//	            this.returnSuccess(response, map);
//	        }
//		}catch (IOException e) {
//			throw new MbvException("暂无物流信息");
		}catch(MbvException e){
    		throw new MbvException("暂无物流信息");
    	}catch(RuntimeException e){
    		throw new MbvException("暂无物流信息");
    	}
    }
    
    @RequestMapping(value = "findOrderChannelName", method = RequestMethod.POST)
	@ResponseBody
	public void findOrderChannelName(HttpServletRequest request, HttpServletResponse response) throws MbvException {
		// 判断vo
		Map<String, Object> map = new HashMap<String, Object>();
		try{
			CodeBean bean = new CodeBean();
			bean.setSysCode(MbvConstant.MBV_ORDER_CHANNEL_CODE);
			List<CodeDtlEntity> list = codeService.selectCodeDtlNameByPara(bean);
			log.info("ReportController.findOrderChannelName -> findOrderChannelName: " + bean);
			if(list != null && list.size() > 0){
				map.put("list", list);
				map.put("success", true);
			}else{
				map.put("status","ERROR");
	        	map.put("reason", "系统忙，请联系管理员！");
			}
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
		// 保存成功
		returnSuccess(response, map);
	}
    
    
    
	
    @RequestMapping(value = "/queryVenRnkByParams", method = RequestMethod.POST)
	@ResponseBody
	public JqGridBaseEntityVo<VenRnkEntity> queryVenRnkByParams(JqGridBaseEntityVo<VenRnkEntity> entity, VenRnkVo vo,
			HttpServletRequest request, HttpServletResponse response) throws MbvException {
		try{
		
		VenRnkBean bean = new VenRnkBean();
		// 如果vo为空，则抛出异常
		if (vo == null || vo.getBean() == null) {
//			 throw new MbvException("传入参数有误！");
//			VenRnkBean bean = new VenRnkBean();
			bean.setOrderId("1");
			bean.setOrderFlag("1");
			bean.setRankNum(30);
		}else{
			
			bean = vo.getBean();
		}
		
		//与组织编码相关
		String warehCode = request.getSession().getAttribute(MbvConstant.WAREH_CODE).toString();
		String unitCode = request.getSession().getAttribute(MbvConstant.UNIT_CODE).toString();
		bean.setWarehCode(warehCode);
		bean.setUnitCode(unitCode);
		
		//校验输入的商品款码   前台已经传入
		if(bean.getProdNumQuery()!=null&&bean.getProdNumQuery()!=""){
			
			String[] arr = bean.getProdNumQuery().split(",");
			bean.setProdNumList(Arrays.asList(arr));
		}
		log.info("ReportController.queryVenRnkByParams -> ProdNum: " + bean.getProdNumQuery());

		String weekFrom = bean.getDate1();
		String weekTo = bean.getDate2();
		String today = "";

		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		today = sdf.format(now);


		String dateFlag = "";

		// date
		if (StringUtils.isNotEmpty(weekFrom) && StringUtils.isNotEmpty(weekTo)) {
			weekFrom = weekFrom.trim();
			weekTo = weekTo.trim();
			if(weekFrom.equals(weekTo)){
				if(weekFrom.length()==10){
					bean.setDate1(weekFrom);
					dateFlag = "day";
				}else{
					bean.setDate1(weekFrom);
					dateFlag = "month";
				}
			}else{
				
				if (weekFrom.length() == 7) {
					weekFrom += "-01";
					bean.setDate1(weekFrom);
				}
				
				if (weekTo.length() == 7) {
					weekTo += "-01";
					bean.setDate2(weekTo);
				}
				
				dateFlag = "week";
			}
			

		} else {
			bean.setDate1(today);

			dateFlag = "day";
		}
		
		bean.setDateFlag(dateFlag);
		log.info("ReportController.queryVenRnkByParams -> weekFrom: " + weekFrom+" weekTo"+weekTo+" dateFlag"+dateFlag);
		log.info("ReportController.queryVenRnkByParams -> bean: " + bean);

		List<VenRnkEntity> list = new ArrayList<VenRnkEntity>();
			DateUtils.start();	
			list = this.reportService.queryVenRnkList(bean);
			
			System.out.println("查询完整list耗时："+DateUtils.stop()+"毫秒");
			log.info("list size:" + list.size());
			
			DateUtils.start();	
			
			String listStr = JSONObject.toJSONString(list);
			StringBuilder builder = new StringBuilder(); builder.append("{");
			builder.append("\"response\":"); builder.append(listStr);
			builder.append("}"); System.out.println(builder.toString());
			JSONObject object = (JSONObject)
			JSONObject.parse(builder.toString());
			
			System.out.println("list拼接解析耗时："+DateUtils.stop()+"毫秒");
			
			outPrintJson(response, object);
		}catch(MbvException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}catch(RuntimeException e){
    		throw new MbvException(MbvConstant.MBV_SYS_ERROR_TIP);
    	}
		return entity;
	}

	@RequestMapping(value = "/charts", method = RequestMethod.POST)
	public void chartsCount(VenRnkBean bean, HttpServletRequest request, HttpServletResponse response)
			throws MbvException, Exception {
		
		String weekFrom = request.getParameter("weekFrom");
		String weekTo = request.getParameter("weekTo");
		String prodNum = request.getParameter("prodNum");
		bean = new VenRnkBean();
		
		//与组织编码相关
		String warehCode = request.getSession().getAttribute(MbvConstant.WAREH_CODE).toString();
		String unitCode = request.getSession().getAttribute(MbvConstant.UNIT_CODE).toString();
		bean.setWarehCode(warehCode);
		bean.setUnitCode(unitCode);
		
		
		if(StringUtils.isEmpty(prodNum)){ 
			 throw new MbvException("商品款码异常！");
		}else{
			if(prodNum.trim().length()==6){
				bean.setProdFlag(true);
			}else{
				bean.setProdFlag(false);
			}
		}
		bean.setProdNum(prodNum);
		
		
		String today = "";

		Map<String, Object> map = new HashMap<String, Object>();
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		today = sdf.format(now);
		
		
		
		SimpleDateFormat sdff = new SimpleDateFormat("yyyy/MM/dd");
		if(StringUtils.isNotEmpty(weekFrom) ){
			Date fromDate = sdff.parse(weekFrom);
			weekFrom = sdf.format(fromDate);
		}
		
		if(StringUtils.isNotEmpty(weekTo) ){
			Date toDate = sdff.parse(weekTo);
			weekTo = sdf.format(toDate);
		}

		String dateFlag = "";

		// date
				if (StringUtils.isNotEmpty(weekFrom) && StringUtils.isNotEmpty(weekTo)) {
					weekFrom = weekFrom.trim();
					weekTo = weekTo.trim();
					if(weekFrom.equals(weekTo)){
						if(weekFrom.length()==10){
							bean.setDate1(weekFrom);
							dateFlag = "day";
						}else{
							bean.setDate1(weekFrom);
							dateFlag = "month";
						}
					}else{
						
						if (weekFrom.length() == 7) {
							weekFrom += "-01";
							bean.setDate1(weekFrom);
						}
						
						if (weekTo.length() == 7) {
							weekTo += "-01";
							bean.setDate2(weekTo);
						}
						
						if (weekFrom.length() == 10) {
	    					bean.setDate1(weekFrom);
	    				}
	    				
	    				if (weekTo.length() == 10) {
	    					bean.setDate2(weekTo);
	    				}
						
						dateFlag = "week";
					}
					

				} else {
					
					if(StringUtils.isNotEmpty(weekFrom)){
						if(weekFrom.length()==10){
							bean.setDate1(weekFrom);
							dateFlag = "day";
						}else if(weekFrom.length()==7){
							bean.setDate1(weekFrom);
							dateFlag = "month";
						}
					}else if(StringUtils.isNotEmpty(weekTo)){
						if(weekTo.length()==10){
							bean.setDate1(weekTo);
							dateFlag = "day";
						}else if(weekTo.length()==7){
							bean.setDate1(weekTo);
							dateFlag = "month";
						}
					}else{
						bean.setDate1(today);
						dateFlag = "day";
					}
					
				}
		
		bean.setDateFlag(dateFlag);
		log.info("ReportController.chartsCount -> weekFrom: " + weekFrom+" weekTo"+weekTo+" dateFlag"+dateFlag);

		if ("day".equals(dateFlag)) {
			// UserEntity user = userService.selectByUserCode(userCode);
			List<ChartBean> chartBeanList = new ArrayList<ChartBean>();
			if(bean.isProdFlag()){
				chartBeanList = reportService.queryProByProNumAndDay(bean);
			}else{
				chartBeanList = reportService.queryProByProNumAndDaySKU(bean);
			}
			
			if (chartBeanList != null && chartBeanList.size() > 0) {
				// 天
				for (ChartBean chartBean : chartBeanList) {
					map.put(chartBean.getHour() + "", chartBean.getTtlVal() + "#" + chartBean.getTtlQty() + "#"
							+ chartBean.getTtlQty() + "#" + chartBean.getTtlQty());
				}
			}

			for (int i = 0; i < 24; i++) {
				if (!map.containsKey(i + "")) {
					map.put(i + "", 0 + "#" + 0 + "#" + 0 + "#" + 0);
				}
			}
		} else if ("week".equals(dateFlag)) {
			List<ChartBean> chartBeanList = new ArrayList<ChartBean>();
			if(bean.isProdFlag()){
				chartBeanList = reportService.queryProByProNumAndWeek(bean);
			}else{
				chartBeanList = reportService.queryProByProNumAndWeekSKU(bean);
			}
			if (chartBeanList != null && chartBeanList.size() > 0) {
				for (ChartBean chartBean : chartBeanList) {
					map.put(chartBean.getHour() + "", chartBean.getTtlVal() + "#" + chartBean.getAmount() + "#"
							+ chartBean.getAmount() + "#" + chartBean.getTtlQty());
				}
			}

			Calendar startDay = Calendar.getInstance();
			Calendar endDay = Calendar.getInstance();

			startDay.setTime(FORMATTER.parse(weekFrom.trim()));
			endDay.setTime(FORMATTER.parse(weekTo.trim()));

			// 给出的日期开始日比终了日大则不执行打印
			if (startDay.compareTo(endDay) >= 0) {
				return;
			}
			// 现在打印中的日期
			Calendar currentPrintDay = startDay;
			while (true) {
				if (!map.containsKey(FORMATTER.format(currentPrintDay.getTime()))) {
					map.put(FORMATTER.format(currentPrintDay.getTime()) + "", 0 + "#" + 0 + "#" + 0 + "#" + 0);
				}

				// 日期加一
				currentPrintDay.add(Calendar.DATE, 1);
				// 日期加一后判断是否达到终了日，达到则终止打印
				if (currentPrintDay.compareTo(endDay) > 0) {
					break;
				}
			}

		} else if ("month".equals(dateFlag)) {
			List<ChartBean> chartBeanList = new ArrayList<ChartBean>();
			if(bean.isProdFlag()){
				chartBeanList = reportService.queryProByProNumAndMonth(bean);
			}else{
				chartBeanList = reportService.queryProByProNumAndMonthSKU(bean);
			}
			if (chartBeanList != null && chartBeanList.size() > 0) {
				for (ChartBean chartBean : chartBeanList) {
					map.put(chartBean.getHour() + "", chartBean.getTtlVal() + "#" + chartBean.getAmount() + "#"
							+ chartBean.getAmount() + "#" + chartBean.getTtlQty());
				}
			}
			Calendar startDay = Calendar.getInstance();
			Calendar endDay = Calendar.getInstance();

            String month = (String) bean.getDate1();
            String tmpMonth = month.split("-")[1];
            
            //获取当前时间
    		Calendar cal = Calendar.getInstance();
    		//下面可以设置月份，注：月份设置要减1，所以设置1月就是1-1，设置2月就是2-1，如此类推
    		cal.set(Calendar.MONTH, Integer.valueOf(tmpMonth)-1);
    		int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    		
            startDay.setTime(FORMATTER.parse(month+"-01"));
            endDay.setTime(FORMATTER.parse(month+"-"+String.valueOf(maxDay)));

			// 给出的日期开始日比终了日大则不执行打印
			if (startDay.compareTo(endDay) >= 0) {
				return;
			}
			// 现在打印中的日期
			Calendar currentPrintDay = startDay;
			while (true) {
				if (!map.containsKey(FORMATTER.format(currentPrintDay.getTime()))) {
					map.put(FORMATTER.format(currentPrintDay.getTime()) + "", 0 + "#" + 0 + "#" + 0 + "#" + 0);
				}

				// 日期加一
				currentPrintDay.add(Calendar.DATE, 1);
				// 日期加一后判断是否达到终了日，达到则终止打印
				if (currentPrintDay.compareTo(endDay) == 0) {
					break;
				}
			}
		}

		JSONObject json = new JSONObject();
		// 判断用户有没有自己的map
		if (map != null && map.keySet().size() > 0) {
			// 循环map，放到jsonObject中
			Iterator<String> keys = map.keySet().iterator();
			while (keys.hasNext()) {
				String key = keys.next();
				json.put(key, map.get(key));
			}
		}
		// 增加success属性
		outPrintJson(response, json.toString());
	}
	
	
}
