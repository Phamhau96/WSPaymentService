import com.misys.tools.integration.api.message.GMessage
import groovy.xml.StreamingMarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.misys.tools.integration.api.annotation.InjectResource
import com.misys.tools.integration.api.component.resource.properties.GPropertiesResource
import groovy.transform.Field
import wslite.soap.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.sql.Sql
import java.sql.*

@Field
@InjectResource(required = true)
GPropertiesResource propertiesResource
final Logger logger = LoggerFactory.getLogger("CITADService")

GMessage message = message
def xmlIn = message.messageBodyAsText
logger.info "xml in = ${xmlIn}"

def inputResult = []
def rootNode = new XmlSlurper().parseText(xmlIn)
rootNode.childNodes().toList().each {value -> inputResult.add(value.text())}

def FileName = inputResult.get(0).toString()
def InputMsg = inputResult.get(1).toString()
def greeting = ""

def dbURL = propertiesResource.getProperty('dbURL')
def dbUsername = propertiesResource.getProperty('dbUsername')
def dbPassword = propertiesResource.getProperty('dbPassword')
def dbDriver = propertiesResource.getProperty('dbDriver')
def db 
try{
	db = Sql.newInstance(dbURL,dbUsername,dbPassword,dbDriver)
}catch (Exception e){
	logger.error "Connect error "
	logger.error e.getMessage()
}

logger.info "dbURL: " + dbURL
String bool = insertCITAD( InputMsg, db, logger)

greeting = bool


def builder = new StreamingMarkupBuilder()
def xmlOut = builder.bind{
		mkp.declareNamespace('':'PaymentsWS.wsdl')
		'PaymentsMsgReply' (greeting)
		}.toString()
logger.info "response created = ${xmlOut}"

db.close()
return message
        .result()
        .successful()
        .messageBody(xmlOut)
        .build()


def String insertCITAD(String data, def db, Logger logger){
	String bool = 'Send Successfully'
	String OPERT1 = "30"
	String Serial_Full = "00000000"
	String FIN_AllContent = new String(data);
	
	String RV_CODE = ''
	String R_Proxy = ''	
	
	String MailID = FIN_AllContent.substring(FIN_AllContent.indexOf(":20:") + 4, FIN_AllContent.indexOf(":79:")).trim()
	String Serial_No = MailID.replaceAll("[^0-9]+", "")
	Serial_No = Serial_No + Serial_Full.substring(Serial_No.length())
	
	String finF79 = FIN_AllContent.substring(FIN_AllContent.indexOf(":79:")+4, FIN_AllContent.indexOf("\n-}"));
	
 	String sql = "INSERT INTO Kustom..TBL_TRANS_OUT_GTW" + "\n" +
				"(CHECK_CODE, CREATE_FILE_RESULT_FLAG, FILE_NAME_RESULT, TRX_TYPE, SD_TIME, SERIAL_NO, RELATION_NO, RESPONSE_CODE, O_CI_CODE, R_CI_CODE, O_INDIRECT_CODE, R_INDIRECT_CODE, FEE_CI_CODE, TRX_DATE, CURRENCY, EXCHANGE_RATE, AMOUNT, SD_CODE, SD_NAME, SD_ADDR, SD_ACCNT, SD_ID_NO, SD_ISSUE_DATE, SD_ISSUER, RV_CODE, RV_NAME, RV_ADDR, RV_ACCNT, RVID_NO, RV_ISSUE_DATE, RV_ISSUER, CONTENT, CUS_TYPE, AUTHORIZED, FEE_FLAG, REFERENCE, EX_E_SIGN, CREATE_TIME, APPR_ID, E_SIGN, OPTION1, OPTION2, OPTION3, SPARE, MAC, FILE_NAME, CONTENFROMFILE, ERR_MSG, LINEPOSITION, FILEPROCESSINGTIME, CONTENT_EX) VALUES " + "\n" +
				"('00','', '', 'TransClasCode', '', 'Serial_No', 'MailID', '', 'O-CICode', 'R-CIcode', 'O_Proxy', 'R_Proxy', 'O-CICode', 'TransDate', 'Currency', '', 'TransAmt', '', 'SendingCust', 'SendCustAdd', 'SendCustAcc', '', '', '', 'rv_code', 'RecCusting', 'RecCustAdd', 'RecCustAcc', '', '', '', 'Reference - PmtDesc', '', '', '', '179:Others', '', '', '', '', '', '', '', '', '', '', '', '', '', '', '')"

		sql = sql.replace("Serial_No", Serial_No.trim())
		sql = sql.replace("Opert1", OPERT1)
		sql = sql.replace("MailID", MailID)
		finF79 = finF79.replaceAll("\\]\\[", " = ")
		int numberOfOpenBlanket = finF79.replaceAll("\\]", "").length()
		int numberOfCloseBlanket = finF79.replaceAll("\\[", "").length()
		int indexOfFirstOpenBlanket = finF79.indexOf("[")

	if ((numberOfOpenBlanket == numberOfCloseBlanket) && (numberOfOpenBlanket > 0) && (indexOfFirstOpenBlanket == 0)) {
		Matcher matcher = Pattern.compile(Pattern.quote("[") + "(.*?)" + Pattern.quote("]")).matcher(finF79)
		while (matcher.find()) {
			String f79Tag = matcher.group(1).substring(0,matcher.group(1).indexOf("=")).replaceAll(" ", "")
			String f79Value = matcher.group(1).substring( matcher.group(1).indexOf("=")+1).trim()
			
			if(f79Tag == '182:Others'){
				R_Proxy = f79Value
			}
			
			f79Tag = (f79Tag == '182:Others') ? 'rv_code' : f79Tag
			
			sql = sql.replace(f79Tag.trim(), f79Value.trim())
			
			
			f79Tag = f79Tag.substring(f79Tag.indexOf(":") + 1).replaceAll(" ", "")
			if(f79Tag == 'R-CIcode'){
				RV_CODE = f79Value
			}
			
			if(f79Tag == 'R_Proxy'){
				R_Proxy = f79Value
				continue 
			}
			
			f79Tag = (f79Tag == 'RecCust') ? "RecCusting" : f79Tag	
			f79Value = (f79Tag == 'TransDate') ? f79Value.substring(0, f79Value.length() - 6) : f79Value
			f79Value = (f79Tag == 'TransCode' && f79Value == "") ? 100 : f79Value
			
 			sql = sql.replace(f79Tag.trim(), f79Value.trim())	
		}
		sql = sql.replace('rv_code', RV_CODE)
		sql = sql.replace('179:Others', '')
		sql = sql.replace('R_Proxy', R_Proxy)
	}
	logger.info "INSERT SQL: " + sql 
	try{		
	
	db.execute(sql);
	bool = "Successfully"
	}
	catch (Exception e){
		logger.error "DB error "
		logger.error e.getMessage()
		bool = 'Error: ' + e.getMessage()
	}
	
	return bool
}