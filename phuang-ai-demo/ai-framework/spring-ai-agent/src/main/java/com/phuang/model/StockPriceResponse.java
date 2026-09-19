package com.phuang.model;

import lombok.Data;

import java.util.List;

@Data
public class StockPriceResponse {

    private List<Stock> stockList;

    private boolean isSuccess;

    private String errorMessage;

}
