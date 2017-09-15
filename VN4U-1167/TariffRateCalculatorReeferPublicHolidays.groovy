import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.extract.ChargeableUnitEvent;
import com.navis.argo.business.services.TieredCalculation;
import com.navis.billing.BillingField;
import com.navis.billing.business.calculators.TariffRateCalculator;
import com.navis.billing.business.model.*;
import com.navis.external.billing.AbstractTariffRateCalculatorInterceptor;
import com.navis.framework.portal.FieldChanges;
import com.navis.framework.util.BizViolation;
import com.navis.framework.business.Roastery;
import org.hibernate.transform.ResultTransformer;
import org.apache.log4j.Level;
import java.text.*;
import java.text.DecimalFormat.*;
import java.text.NumberFormat.*;
import java.util.Locale.*;
// corrected by Rudi Coats SofenConsultants 16MAY2017
//



public class TariffRateCalculatorReeferPublicHolidays extends AbstractTariffRateCalculatorInterceptor {

	private StringBuilder logDev = new StringBuilder();
	private StringBuilder logUsr = new StringBuilder();
	private SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	private NumberFormat FormatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt","BR"));
	private SimpleDateFormat fmt2 = new SimpleDateFormat("dd/MM");
	public void calculateRate(Map inOutMap) {
		logDev.setLength(0);
		logUsr.setLength(0);
		
		Invoice inv = (Invoice) inOutMap.get("inInvoice");

		TariffRate tariffRate = (TariffRate) inOutMap.get("inTariffRate");
		ChargeableUnitEvent chrgUnitEvt = (ChargeableUnitEvent) inOutMap.get("inExtractEvent");
	
        Date invoicepaidnew= new Date();

		TieredCalculation calcInput = TariffRateCalculator.getTieredCalculation(inv, chrgUnitEvt, invoicepaidnew, null);
		double qtyPaid = calcInput.getQtyPaid();
		double qtyOwed = calcInput.getQtyOwed();
		
		//logEx("N4-Inventory called qtyPaid:" + qtyPaid + " qtyOwed:" + qtyOwed +
		//	" FirstPaidDay:" + calcInput.getFirstPaidDay() + " PrevPaidThruDay:" + calcInput.getPrevPaidThruDay() + " PaidThruDay:" + calcInput.getPaidThruDay(), inv);
		//replaced the below with the call from the invoice paid thru day paramter
		//Date itemTo = calcInput.getPaidThruDay();
		Date itemTo = inv.getInvoicePaidThruDay();
		int qtyDays=0;
		Double outRateAmount = tariffRate.getRateAmount();
		Calendar start = Calendar.getInstance();
		Date itemFrom= calcInput.getFirstPaidDay();
		
		start.setTime(itemFrom);
		
				
		Calendar end = Calendar.getInstance();
		end.setTime(itemTo);
		
		logUsr.append("qtyPa= ").append(qtyPaid).append("\n");
		logUsr.append("qtyOw= ").append(qtyOwed).append("\n");
		logUsr.append("oRateAmount=").append(outRateAmount).append("\n");
		logUsr.append("From=").append(itemFrom).append("\n");
		logUsr.append("To=").append(fmt.format(itemTo)).append("\n");
					
		List calendar=calendarDays();
		
		 while( !start.after(end)){
			Date targetDay = start.getTime();
			if (searchCalendarPublicHolidays(targetDay,calendar)){
				qtyDays++;
				logUsr.append("PH=").append(fmt.format(targetDay)).append("\n");
			}
										  
			start.add(Calendar.DATE, 1);
		}
		 
		Double outAmount=outRateAmount*qtyDays
		
	    logEx("Calculating Monitorng PublicHolidays: eventChargesAmountTotal:" + outAmount, inv);
		
		inOutMap.put("outAmount", outAmount);
		FieldChanges fieldChanges = new FieldChanges();
		//removed below and replaced it with the paid thru day entered in the invoice paramters
		//fieldChanges.setFieldChange(BillingField.ITEM_PAID_THRU_DAY, calcInput.getPaidThruDay());
		fieldChanges.setFieldChange(BillingField.ITEM_PAID_THRU_DAY, inv.getInvoicePaidThruDay());
		fieldChanges.setFieldChange(BillingField.ITEM_PREV_PAID_THRU_DAY, calcInput.getPrevPaidThruDay());
		fieldChanges.setFieldChange(BillingField.ITEM_FROM_DATE, truncDay(itemFrom));
		fieldChanges.setFieldChange(BillingField.ITEM_TO_DATE, truncDay(itemTo));
	
		
		if (logUsr.length() > 1000) logUsr.setLength(1000);
		fieldChanges.setFieldChange(BillingField.ITEM_NOTES, logUsr.toString());
		
	
		fieldChanges.setFieldChange(BillingField.ITEM_QUANTITY_BILLED, qtyDays*1.00D);
		fieldChanges.setFieldChange(BillingField.ITEM_RATE_BILLED, tariffRate.getRateAmount());
		fieldChanges.setFieldChange(BillingField.ITEM_AMOUNT, outAmount);
		inOutMap.put("invItemChanges", fieldChanges);
	}

	public List calendarDays() throws BizViolation {
		GroovyApi groovyApi = new GroovyApi();
		ResultTransformer resultTransformer = (ResultTransformer) groovyApi.getGroovyClassInstance("ResultTransformerMap");
		String sql = "select SUBSTRING(CONVERT(VARCHAR(10),ce.occ_start,103),1,5) from argo_calendar c " +
						"join argo_cal_event ce on  ce.calendar_gkey=c.gkey  " +
						"join argo_cal_event_type cet on cet.gkey=ce.event_type_gkey " +
						"where c.id= :idCalendar " +
						"and cet.name='EXEMPT_DAY' " +
						"and ce.repeat_interval='ANNUALLY'";

		List calendarDays = Roastery.getHibernateApi().getCurrentSession().createSQLQuery(sql)
				.setParameter("idCalendar", "PUBLIC_HOLIDAY")
				.setResultTransformer(resultTransformer)
				.list();
				
		return calendarDays;
	}
	
	public boolean searchCalendarPublicHolidays(Date dateSearch, List calendar){
		boolean a;
   String dateSearchfmt =fmt2.format(dateSearch);

	   String datemap="";
		 
	   for (int i = 0; i < calendar.size(); i++) {
		 Map<String> chrgRow = (Map<String>) calendar.get(i);

		String  datecomp=chrgRow.values().toString();
				
		 if (datecomp.contains(dateSearchfmt)) return true;
		 else a=false;
		
	 }
				  
	 return a;
		   
	}
	
		
	public static Long deltaDays(Date dateBase, Date dateSubstract) {
		if (dateSubstract == null || dateBase == null) return null;
		long milis = truncDay(dateBase).getTime() - truncDay(dateSubstract).getTime();
		return milis / (1000 * 60 * 60 * 24);
	}

	public static Date truncDay(Date date) {
		if (date == null) return null;
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTime();
	}

	public static Date addDays(Date date, int days) {
		if (date == null) return null;
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.DAY_OF_YEAR, days);
		return cal.getTime();
	}

	private void logEx(String str, Invoice inv) {
		log(Level.INFO, str);
		logDev.append(str).append("|\n");
		InvoiceMessage.registerDebug(inv, "DAYS:" + str);
	}
	
   
}