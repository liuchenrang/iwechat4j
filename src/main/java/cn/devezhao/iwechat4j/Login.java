package cn.devezhao.iwechat4j;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import cn.devezhao.iwechat4j.utils.UnexpectedResultException;
import cn.devezhao.iwechat4j.utils.Utils;

/**
 * 登录及初始化基础数据
 * 
 * @author zhaofang123@gmail.com
 * @since 05/10/2017
 */
public class Login {

	private Wechat wechat;
	
	public Login(Wechat wechat) {
		this.wechat = wechat;
	}
	
	/**
	 * 获取登录 QR
	 * 
	 * @return
	 */
	public File getLoginQR() {
		String uuid = getQrUuid();
		wechat.getSession().addAttr("uuid", uuid);
		String url = String.format("%s/qrcode/%s", Config.LOGIN_URL, uuid);
		
		File qr = new File(Config.DATA_DIR, "iwechat4j/qr");
		if (!qr.exists()) {
			qr.mkdirs();
		}
		qr = new File(qr, System.currentTimeMillis() + ".jpg");
		
		Utils.downloadFile(wechat.getHttpClient(), qr, url);
		return qr;
	}
	
	/**
	 * 检查登录（扫码）状态
	 * 
	 * @return
	 */
	public int checkStatus() {
		String url = String.format("%s/cgi-bin/mmwebwx-bin/login?loginicon=true&uuid=%s&tip=0&r=%d",
				Config.LOGIN_URL, wechat.getSession().getAttr("uuid"), System.currentTimeMillis());
		String r = wechat.getHttpClient().get(url);
		
		String regex = "window.code=(\\d+)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(r);
		if (matcher.find()) {
			String status = matcher.group(1);
			int s = Integer.parseInt(status);
			if (s == 200) {
				try {
					initWebSession(r);
				} catch (DocumentException e) {
					throw new UnexpectedResultException(e);
				}
			}
			return s;
		}
		throw new UnexpectedResultException(r);
	}
	
	/**
	 * 获取 QR UUID
	 * 
	 * @return
	 */
	protected String getQrUuid() {
		String url = String.format("%s/jslogin?appid=wx782c26e4c19acffb&fun=new", Config.LOGIN_URL);
		String r = wechat.getHttpClient().get(url);
		
		String regex = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\";";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(r);
		if (matcher.find()) {
			String uuid = matcher.group(2);
			return uuid;
		}
		throw new UnexpectedResultException("获取 QR UUID 错误: " + r);
	}
	
	/**
	 * 存储会话数据
	 * 
	 * @param content
	 * @throws DocumentException 
	 */
	private void initWebSession(String content) throws DocumentException {
		// 1.
		String regex = "window.redirect_uri=\"(\\S+)\"";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(content);
		String redirectUrl = null;
		if (matcher.find()) {
			redirectUrl = matcher.group(1);
			String baseUrl = redirectUrl.substring(0, redirectUrl.lastIndexOf('/'));
			wechat.getSession().addAttr("baseUrl", baseUrl);
		} else {
			throw new UnexpectedResultException(content);
		}
		
		HttpGet get = new HttpGet(redirectUrl);
		get.setConfig(RequestConfig.custom().setRedirectsEnabled(false)
				.setConnectTimeout(Config.TIMEOUT).setSocketTimeout(Config.TIMEOUT).build());
		HttpResponse resp = wechat.getHttpClient().execute(get);
		String respText = null;
		try {
			respText = EntityUtils.toString(resp.getEntity());
		} catch (ParseException e) {
			throw new UnexpectedResultException(e);
		} catch (IOException e){
			throw new UnexpectedResultException(e);
		}
		Element root = DocumentHelper.parseText(respText).getRootElement();
		
		String attrKeys[] = new String[] { "skey", "wxsid", "wxuin", "pass_ticket" };
		for (String k : attrKeys) {
			wechat.getSession().addAttr(k, root.selectSingleNode("//" + k).getText());
		}
		wechat.getSession().addAttr("deviceid", "e" + String.valueOf(new Random().nextLong()).substring(1, 16));
		
		// 2.
		String url = String.format("%s/webwxinit?&r=%d", Config.BASE_URL, System.currentTimeMillis());
		Map<String, Map<String, String>> dataMap = new HashMap<String, Map<String, String>>();
		dataMap.put("BaseRequest", wechat.getSession().getBaseRequest());
		String data = JSON.toJSONString(dataMap);
		String rs = wechat.getHttpClient().postJson(url, data);
		BaseResponse bresp = new BaseResponse(rs);
		if (!bresp.isSuccess()) {
			throw new UnexpectedResultException(rs);
		}
		
		JSONObject user = bresp.getResponse().getJSONObject("User");
		wechat.getSession().setUserRaw(user);
		JSONObject syncKey = bresp.getResponse().getJSONObject("SyncKey");
		wechat.getSession().setSyncKeyRaw(syncKey);
		// ContactList -> 最近联系人
		
		// 3.
		url = String.format("%s/webwxstatusnotify?lang=zh_CN&pass_ticket=%s",
				wechat.getSession().getBaseUrl(), wechat.getSession().getPassTicket());
		Map<String, Object> dataMap2 = new HashMap<String, Object>();
		dataMap2.put("BaseRequest", wechat.getSession().getBaseRequest());
		dataMap2.put("Code", 3);
		dataMap2.put("FromUserName", wechat.getSession().getUserName());
		dataMap2.put("ToUserName", wechat.getSession().getUserName());
		dataMap2.put("ClientMsgId", System.currentTimeMillis());
		data = JSON.toJSONString(dataMap2);
		rs = wechat.getHttpClient().postJson(url, data);
		bresp = new BaseResponse(rs);
		if (!bresp.isSuccess()) {
			throw new UnexpectedResultException(rs);
		}
	}
}
