package com.pearson.statspoller.utilities.db_utils;

/**
 * @author Jeffrey Schmidt
 */
public interface DatabaseObject<T> {
    
    public boolean isEqual(T t);
    
}
