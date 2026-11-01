package com.phuang.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;


/**
 *
 * @description 天气工具
 * @author huangpeng
 * @since 2026/3/19
 */
public class WeatherTool {

    @Tool(name = "WeatherTool", description = "获取城市天气")
    public String getWeather(@ToolParam(name = "city", description = "城市名称") String city) {
        return city + "当前的天气是多云";
    }
}
