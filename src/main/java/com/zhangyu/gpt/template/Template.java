package com.zhangyu.gpt.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Template {

    public static String getOpportunityListTemp() {
        return "规则:" + "\n" +
                "page:页码,表示要查看第几页的数据,默认值是1;" + "\n" +
                "pageSize:商机条数,例如取几条,前几条,将数字填入,默认值是10;最大值为10;" + "\n" +
                "organization:查询的人员或者组织名称,默认为null;" + "\n" +
                "sort:name为排序字段的名称,sortDirect为排序方式,1为正序排序,2为倒序。如果按照创建时间排序则name值为createTime,如果按照实际成交金额排序则name值为actualMoney,如果按照预计成交金额排序则name值为expectMoney,其余字段的排序将sort置为null。" + "\n" +
                "请按照以上的规则,将需求的内容按照模板进行格式化,模板如下:" + "\n" +
                "{" + "\n" +
                "\t" + "\"page\":1," + "\n" +
                "\t" + "\"pageSize\":10," + "\n" +
                "\t" + "\"organizationName\":\"\"," + "\n" +
                "\t" + "\"sort\":[{\"name\":\"\",\"sortDirect\":\"\"}]" + "\n" +
                "}";
    }
}
