package com.phuang.tools;

import com.phuang.model.WeatherRequest;

import java.util.function.Function;

/**
 *
 * @description WeatherFunction
 * @author huangpeng
 * @since 2026/3/19
 */
public class WeatherFunction implements Function<WeatherRequest, String> {

    @Override
    public String apply(WeatherRequest weatherRequest) {
        return weatherRequest.getLocation() + "当前的天气是多云";
    }
}
