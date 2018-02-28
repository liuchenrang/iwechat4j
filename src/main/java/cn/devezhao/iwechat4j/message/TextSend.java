package cn.devezhao.iwechat4j.message;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;

import cn.devezhao.iwechat4j.BaseResponse;
import cn.devezhao.iwechat4j.Wechat;

/**
 * 
 * @author zhaofang123@gmail.com
 * @since 05/10/2017
 */
public class TextSend implements Send {
	
	private String toUserName;
	private String content;
	
	/**
	 * @param toUserName
	 * @param content
	 */
	public TextSend(String toUserName, String content) {
		this.toUserName = toUserName;
		this.content = content;
	}

	@Override
	public boolean send(Wechat wechat) {
		String url = String.format("%s/webwxsendmsg", wechat.getSession().getBaseUrl());
		Map<String, Object> dataMap = new HashMap<String, Object>();
		dataMap.put("BaseRequest", wechat.getSession().getBaseRequest());
		dataMap.put("Scene", 0);
		
		Map<String, Object> msg = new HashMap<String, Object>();
		msg.put("Type", MessageType.Text.getNumber());
		msg.put("Content", content);
		msg.put("FromUserName", wechat.getSession().getUserName());
		msg.put("ToUserName", toUserName);
		msg.put("LocalID", System.currentTimeMillis());
		msg.put("ClientMsgId", System.currentTimeMillis());
		dataMap.put("Msg", msg);
		
		String data = JSON.toJSONString(dataMap);
		String rs = wechat.getHttpClient().postJson(url, data);
		BaseResponse bresp = new BaseResponse(rs);
		return bresp.isSuccess();
	}
}
