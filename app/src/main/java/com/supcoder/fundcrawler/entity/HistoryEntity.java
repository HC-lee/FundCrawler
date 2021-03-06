package com.supcoder.fundcrawler.entity;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

/**
 * HistoryEntity
 *
 * @author lee
 * @date 2017/11/14
 */

public class HistoryEntity extends RealmObject {

    @PrimaryKey
    private String fundId;


    public String getFundId() {
        return fundId;
    }

    public void setFundId(String fundId) {
        this.fundId = fundId;
    }
}
