package com.ipe.module.exl.service;

import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSON;
import com.ipe.common.dao.BaseDao;
import com.ipe.common.dao.SpringJdbcDao;
import com.ipe.common.exception.CustException;
import com.ipe.common.exception.Exceptions;
import com.ipe.common.service.BaseService;
import com.ipe.common.service.CommonUtil;
import com.ipe.module.exl.ExcelCreate;
import com.ipe.module.exl.ExcelParse;
import com.ipe.module.exl.TableColumn;
import com.ipe.module.exl.dao.ExlImptplDao;
import com.ipe.module.exl.entity.ExlImptpl;
import com.ipe.module.exl.entity.ExlImptplDetailes;


/**
 * MYSQL版本 Excel导入;
 * @author tangdu
 *
 */
@Service
@Transactional
public class ExlImptplService extends BaseService<ExlImptpl, String> {
    @Autowired
    private ExlImptplDao exlImptplDao;
    @Autowired
    private SpringJdbcDao springJdbcDao;

    @Override
    public BaseDao<ExlImptpl, String> getBaseDao() {
        return exlImptplDao;
    }

    public void createTable(String tableName, List<ExlImptplDetailes> detaileses) {
        StringBuilder stringBuilder = new StringBuilder("create table if not exists ");
        stringBuilder.append(tableName).append(" (");
        for (ExlImptplDetailes de : detaileses) {
            stringBuilder.append(" ").append(de.getTableCol()).append(" ").append(de.getColType()).append(" ,");
        }
        String sql = stringBuilder.substring(0, stringBuilder.lastIndexOf(",")) + " )";
        springJdbcDao.execute(sql);
    }

    @Transactional(readOnly = false)
    public void save(ExlImptpl exlImptpl, String details) {
        List<ExlImptplDetailes> detaileses = JSON.parseArray(details, ExlImptplDetailes.class);
        for (ExlImptplDetailes de : detaileses) {
            de.setExlImptpl(exlImptpl);
        }
        exlImptpl.setDetailesSet(detaileses);
        this.save(exlImptpl);
        if("01".equals(exlImptpl.getExlType())){
        	createTable(exlImptpl.getMappingTable(), detaileses);
        }
    }

    @Transactional(readOnly = false)
    public void edit(ExlImptpl exlImptpl, String details) {
        try {//删除表
        	if("01".equals(exlImptpl.getExlType())){
        		String sql = "drop table " + exlImptpl.getMappingTable();
                springJdbcDao.execute(sql);
        	}
        } catch (Exception e) {
        }
        //新增
        this.delete(exlImptpl.getId());
        save(exlImptpl, details);
    }

    @Transactional(readOnly = false)
    public String impData(String appendixPath, String id) {
        try {
            ExlImptpl exlImptpl = this.get(id);
            StringBuilder stringBuilder = new StringBuilder("insert into " + exlImptpl.getMappingTable() + " values (");
            int pocot = exlImptpl.getDetailesSet().size();
            for (int i = 0; i < pocot; i++) {
                stringBuilder.append("?,");
            }
            List<ExlImptplDetailes> detaileses = exlImptpl.getDetailesSet();
            ExcelParse excelParse = new ExcelParse(appendixPath);
            ArrayList<Object[]> arrayList = excelParse.read(exlImptpl.getSheetIndex(), exlImptpl.getStartrowIndex(), exlImptpl.getStartcolIndex());
            String sql = stringBuilder.substring(0, stringBuilder.lastIndexOf(","))+ ")";
            int successCot = 0;
            int failureCot = 0;

            Object[] params = new Object[detaileses.size()];
            for (Object[] obj : arrayList){
            	int c_len=obj.length;
                try {
                    for (int i = 0; i < detaileses.size(); i++) {
                        ExlImptplDetailes imptplDetailes = detaileses.get(i);
                        int col=imptplDetailes.getExlCol()-1;//对应的列
						if(col>=c_len){
							params[i] = null;
							continue;
						}
						String val_=obj[col].toString();//对应列的值
						if (StringUtils.isBlank(val_)) {
							params[i] = null;
						} else if (imptplDetailes.getDefValue() != null && !"".equals(imptplDetailes.getDefValue())) {
							params[i]=getValue(imptplDetailes.getColType(), imptplDetailes.getDefValue());
                        } else {
                        	params[i]=getValue(imptplDetailes.getColType(), obj[i]);
                        }
                    }
                    springJdbcDao.update(sql, params);
                    successCot++;
                } catch (Exception e) {
                    failureCot++;
                    LOGGER.error("IMPERROR ",e);
                }
            }
            return "{successCot:" + successCot + ",failureCot:" + failureCot + ",total:" + arrayList.size() + "}";
        } catch (Exception e) {
            throw Exceptions.throwServiceException(e);
        }
    }

    final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");//only support this

    public Object getValue(String type, Object val) {
        if (type.startsWith("varchar")) {//字符串
            return val;
        } else if (type.startsWith("timestamp")) {
            try {
                return dateFormat.parse(val.toString());
            } catch (ParseException e) {
                e.printStackTrace();
                return null;
            }
        } else if (type.startsWith("integer")) {
            return Integer.valueOf(val.toString());
        } else if (type.startsWith("double")) {
            return Double.valueOf(val.toString());
        }
        return val;
    }
    
    public void expData(String id,OutputStream outputStream) throws CustException{
    	ExlImptpl exlImptpl = this.get(id);
    	List<TableColumn> info=getTableInfo(exlImptpl.getMappingTable(),exlImptpl.getTableBelongUser());
    	List<Map<String, Object>> data=springJdbcDao.queryListMap(" select * from "+exlImptpl.getTableBelongUser()+"."+exlImptpl.getMappingTable());
    	ExcelCreate.getInstance().createDefault(data, info, outputStream);
    }
    
    @Autowired
    private CommonUtil  commonUtil;
    
	public List<TableColumn> getTableInfo(String mappingTable,String tableBelongUser){
    	return commonUtil.getTableInfo(mappingTable, tableBelongUser);
    }
}
