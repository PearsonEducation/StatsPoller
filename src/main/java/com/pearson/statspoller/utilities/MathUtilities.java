package com.pearson.statspoller.utilities;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jeffrey Schmidt
 */
public class MathUtilities {
    
    private static final Logger logger = LoggerFactory.getLogger(MathUtilities.class.getName());
    
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    
    public static boolean areBigDecimalsNumericallyEqual(BigDecimal bigDecimal1, BigDecimal bigDecimal2) {
        
        boolean isEqual = false;
        
        if ((bigDecimal1 != null) && (bigDecimal2 != null)) {
            isEqual = (bigDecimal1.compareTo(bigDecimal2) == 0);
        }
        else if (bigDecimal1 == null) {
            isEqual = (bigDecimal2 == null);
        }
        
        return isEqual;
    }
    
    public static Long computeMedianOfLongs(List<Long> numbers) {
        
        if ((numbers == null) || (numbers.isEmpty())) {
            return null;
        }
        
        if (numbers.size() == 1) {
            return numbers.get(0);
        }
        
        Long median;
        
        List<Long> localNumbers = new ArrayList(numbers);
        Collections.sort(localNumbers);
        
        boolean isOddSized = (localNumbers.size() % 2) == 1;
        int medianIndex = localNumbers.size() / 2;

        if (isOddSized) {
            median = localNumbers.get(medianIndex);
        }
        else {
            median = (localNumbers.get(medianIndex - 1) + localNumbers.get(medianIndex)) / 2;
        }        
    
        return median;
    }
    
    public static Double computeMedianOfDoubles(List<Double> numbers) {
        
        if ((numbers == null) || (numbers.isEmpty())) {
            return null;
        }
        
        if (numbers.size() == 1) {
            return numbers.get(0);
        }
        
        Double median;
        
        List<Double> localNumbers = new ArrayList(numbers);
        Collections.sort(localNumbers);
        
        boolean isOddSized = (localNumbers.size() % 2) == 1;
        int medianIndex = localNumbers.size() / 2;

        if (isOddSized) {
            median = localNumbers.get(medianIndex);
        }
        else {
            median = (localNumbers.get(medianIndex - 1) + localNumbers.get(medianIndex)) / 2;
        }        
    
        return median;
    }
    
    public static BigDecimal computeMedianOfBigDecimals(List<BigDecimal> numbers, MathContext mathContext, boolean areNumbersAlreadySorted) {
        
        if ((numbers == null) || (numbers.isEmpty())) {
            return null;
        }
        
        if (numbers.size() == 1) {
            return numbers.get(0);
        }
        
        BigDecimal median;
        
        List<BigDecimal> sortedNumbers;
        if (!areNumbersAlreadySorted) {
            sortedNumbers = new ArrayList(numbers);
            Collections.sort(sortedNumbers);
        }
        else {
            sortedNumbers = numbers;
        }
        
        boolean isOddSized = (sortedNumbers.size() % 2) == 1;
        int medianIndex = sortedNumbers.size() / 2;

        if (isOddSized) {
            median = sortedNumbers.get(medianIndex);
        }
        else {
            median = sortedNumbers.get(medianIndex - 1).add(sortedNumbers.get(medianIndex));
            median = median.divide(new BigDecimal(2), mathContext);
        }        
    
        return median;
    }

    public static BigDecimal smartBigDecimalScaleChange(BigDecimal number, int scale, RoundingMode roundingMode) {
        
        if ((number == null) || (roundingMode == null)) {
            return null;
        }
        
        if ((number.scale() >= 0) && (scale >= 0)) {
            if (number.scale() <= scale) {
                return number;
            }
            else {
                return number.setScale(scale, roundingMode);
            }
        }
        else {
            return number.setScale(scale, roundingMode);
        }
    }
    
    public static Long getSmallestValue(List<Long> values) {
        
        if ((values == null) || values.isEmpty()) {
            return null;
        }
        
        Long returnValue = null;
        int counter = 0;
        
        for (Long currentValue : values) {
            if (counter == 0) {
                returnValue = currentValue;
            }
            else if ((counter > 0) && (returnValue > currentValue)) {
                returnValue = currentValue;
            }
            
            counter++;        
        }
        
        return returnValue;
    }

    public static BigDecimal correctOutOfRangePercentage(BigDecimal input) {
        
        if (input == null) {
            return null;
        }
        
        if (input.compareTo(ONE_HUNDRED) > 0) {
            return ONE_HUNDRED;
        }
        
        if (input.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        
        return input;
    }
    
    public static BigDecimal safeGetBigDecimal(String numericString) {
        
        if (numericString == null) {
            return null;
        }
        
        BigDecimal bigDecimal;

        try {
            bigDecimal = new BigDecimal(numericString);
        }
        catch (Exception e) {
            logger.error(e.toString() + System.lineSeparator() + StackTrace.getStringFromStackTrace(e));
            bigDecimal = null;
        }

        return bigDecimal;
    }
    
}
