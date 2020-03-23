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
	sendMailNotify(-1, "ERROR database", e.getMessage())
}

logger.info "dbURL: " + dbURL
def map = getFieldData(InputMsg)
logger.info "map" + map
def matchField = matchFieldCitad(map)
logger.info "matchField" + matchField
String bool = executeInsert(matchField, db, logger)

greeting = bool

def builder = new StreamingMarkupBuilder()
def xmlOut = builder.bind{
		mkp.declareNamespace('':'PaymentsWS.wsdl')
		'PaymentsMsgReply' (greeting)
		}.toString()
logger.info "response created = ${xmlOut}"

if( db != null){
	db.close()
}
return message
        .result()
        .successful()
        .messageBody(xmlOut)
        .build()


def getFieldData(def msgCitad){
	def map = [:]

	String RV_CODE = ""
	String R_Proxy = ""
	String Serial_Full = "00000000"
	
	try{
		String MailID = msgCitad.substring(msgCitad.indexOf(":20:") + 4, msgCitad.indexOf(":79:")).trim()
		map.put("MailID", MailID)
		
		String Serial_No = MailID.replaceAll("[^0-9]+", "")
		Serial_No = Serial_No + Serial_Full.substring(Serial_No.length())
		map.put("Serial_No", Serial_No)
		
		msgCitad = msgCitad.substring(msgCitad.indexOf(":79:")+4, msgCitad.indexOf("\n-}"));
		def finF79 = msgCitad.replaceAll("\\]\\[", " = ")
		int numberOfOpenBlanket = finF79.replaceAll("\\]", "").length()
		int numberOfCloseBlanket = finF79.replaceAll("\\[", "").length()
		int indexOfFirstOpenBlanket = finF79.indexOf("[")
		
		if ((numberOfOpenBlanket == numberOfCloseBlanket) && (numberOfOpenBlanket > 0) && (indexOfFirstOpenBlanket == 0)) {
			Matcher matcher = Pattern.compile(Pattern.quote("[") + "(.*?)" + Pattern.quote("]")).matcher(finF79)
			while (matcher.find()) {
				String f79Tag = matcher.group(1).substring(0,matcher.group(1).indexOf("=")).replaceAll(" ", "")
				String f79Value = matcher.group(1).substring( matcher.group(1).indexOf("=")+1).trim()
		
				if(f79Tag == "182:Others"){
					R_Proxy = f79Value
				}
		
				f79Tag = (f79Tag == "182:Others") ? "RV_Code" : f79Tag
		
				f79Tag = f79Tag.substring(f79Tag.indexOf(":") + 1).replaceAll(" ", "")
				if(f79Tag == "R-CIcode"){
					RV_CODE = f79Value
					map.put("RV_Code", f79Value)
				}
		
				if(f79Tag == "R_Proxy"){
					R_Proxy = f79Value
					map.put(f79Tag, f79Value)
					continue
				}
				
				f79Tag = (f79Tag == "RecCust") ? "RecCusting" : f79Tag
				f79Value = (f79Tag == "TransDate") ? f79Value.substring(0, f79Value.length() - 6) : f79Value
				f79Value = (f79Tag == "TransCode" && f79Value == "") ? 100 : f79Value
		
				map.put(f79Tag, f79Value)
			}
		}
		
		def others = map.get("Others")
		
		if(others.indexOf("VSDGOVBOND") >= 0 ){
			if(map.get("Currency").equals("VND")){
				map.put("TransClasCode", "201301")
			}else if(map.get("Currency").equals("USD")){
				map.put("TransClasCode", "201311")	
			}else if(map.get("Currency").equals("EUR")){
				map.put("TransClasCode", "201321")
			}
		}else{
			if(map.get("Currency").equals("VND")){
				map.put("TransClasCode", "201001")
			}else if(map.get("Currency").equals("USD")){
				map.put("TransClasCode", "201011")	
			}else if(map.get("Currency").equals("EUR")){
				map.put("TransClasCode", "201021")
			}
		}
		
		if(map.get("TransCode").trim().equals("901") 
			|| map.get("TransCode").trim().equals("902") 
			|| map.get("TransCode").trim().equals("903")){
			
			if(others.indexOf("TTLNH") >= 0){
				others = others.substring(0, others.indexOf("TTLNH"))
				if(others.length() >= 10){
					others = others.substring(others.length() - 10, others.length())
				}
				map.put("Others", others)
			}
		}
		else if(others.length() >= 10){
			others = others.substring(others.length() - 10, others.length())
			map.put("Others", others)
		}
		
		def amount = map.get("TransAmt")
		if(amount.contains(",")){
			if(map.get("Currency").equals("VND")){
				amount = amount + "00"
			}
		}else{
			amount = amount
		}
		map.put("TransAmt", amount)
	}catch(Exception e){
		sendMailNotify(0, "ERROR format dien IBPS: ", e.toString())
	}
	return map;
}

def matchFieldCitad(def map){
	def mapMatch = [:]
	def arrField = ["CHECK_CODE","CREATE_FILE_RESULT_FLAG","FILE_NAME_RESULT","TRX_TYPE","SD_TIME","SERIAL_NO","RELATION_NO","RESPONSE_CODE","O_CI_CODE","R_CI_CODE","O_INDIRECT_CODE","R_INDIRECT_CODE","FEE_CI_CODE","TRX_DATE","CURRENCY","EXCHANGE_RATE","AMOUNT","SD_CODE","SD_NAME","SD_ADDR","SD_ACCNT","SD_ID_NO","SD_ISSUE_DATE","SD_ISSUER","RV_CODE","RV_NAME","RV_ADDR","RV_ACCNT","RV_ID_NO","RV_ISSUE_DATE","RV_ISSUER","CONTENT","CUS_TYPE","AUTHORIZED","FEE_FLAG","REFERENCE","EX_E_SIGN","CREATE_TIME","APPR_ID","E_SIGN","OPTION1","OPTION2","OPTION3","SPARE","MAC","FILE_NAME","CONTENFROMFILE","ERR_MSG",	"LINEPOSITION","FILEPROCESSINGTIME","CONTENT_EX"]

	for(int i = 0; i < arrField.size(); i++){
		mapMatch << matchFileDetail(map, arrField[i])
	}

	return mapMatch;
}


def matchFileDetail(def map, def fieldName){
	
	def prefix = "TEST_UAT2"
	def matchField = [:]
	switch(fieldName){
		case "CHECK_CODE":
			matchField.put(fieldName, "00")
			break;
		case "TRX_TYPE":
			matchField.put(fieldName, map.get("TransClasCode"))
			break;
		case "SERIAL_NO":
			matchField.put(fieldName, map.get("Serial_No"))
			break;
		case "RELATION_NO":
			//matchField.put(fieldName, map.get("MailID"))
			matchField.put(fieldName, map.get("Reference"))
			break;
		case "O_CI_CODE":
			matchField.put(fieldName, map.get("O-CICode"))
			break;
		case "R_CI_CODE":
			matchField.put(fieldName, map.get("R-CIcode"))
			break;
		case "O_INDIRECT_CODE":
			matchField.put(fieldName, map.get("O_Proxy"))
			break;
		case "R_INDIRECT_CODE":
			matchField.put(fieldName, map.get("R_Proxy"))
			break;
		case "FEE_CI_CODE":
			matchField.put(fieldName, map.get("O-CICode"))
			break;
		case "TRX_DATE":
			def ValDate = map.get("ValDate")
			def trx_date = ValDate.substring(6, 10)+ValDate.substring(3, 5)+ValDate.substring(0, 2)
			matchField.put(fieldName, trx_date)
			//matchField.put(fieldName, map.get("TransDate"))
			break;
		case "CURRENCY":
			matchField.put(fieldName, map.get("Currency"))
			break;
		case "AMOUNT":
			matchField.put(fieldName, map.get("TransAmt"))
			break;
		case "SD_NAME":
			//def sd_Name = prefix + map.get("SendingCust") 
			//if(sd_Name.length() > 70){
			//	sd_Name = sd_Name.substring(0, 70)
			//}
			//matchField.put(fieldName, sd_Name)
			matchField.put(fieldName, map.get("SendingCust"))
			break;
		case "SD_ADDR":
			matchField.put(fieldName, map.get("SendCustAdd"))
			break;
		case "SD_ACCNT":
			//matchField.put(fieldName, "12345678")
			matchField.put(fieldName, map.get("SendCustAcc"))
			break;
		case "RV_CODE":
			matchField.put(fieldName, map.get("RV_Code"))
			break;
		case "RV_NAME":
			//def rv_Name = prefix + map.get("RecCusting") 
			//if(rv_Name.length() > 70){
			//	rv_Name = rv_Name.substring(0, 70)
			//}
			//matchField.put(fieldName, rv_Name)
			matchField.put(fieldName, map.get("RecCusting"))
			break;
		case "RV_ADDR":
			matchField.put(fieldName, map.get("RecCustAdd"))
			break;
		case "RV_ACCNT":
			//matchField.put(fieldName, "12345678")
			matchField.put(fieldName, map.get("RecCustAcc"))
			break;
		case "CONTENT":
			//def content = prefix + map.get("Reference") + " - " + map.get("PmtDesc")
			//if(content.length() > 210){
			//	content = content.substring(0, 10)
			//}
			//matchField.put(fieldName, content)
			matchField.put(fieldName,map.get("PmtDesc"))
			break;
		case "REFERENCE":
			matchField.put(fieldName, map.get("Others"))
			break;
		case "LINEPOSITION":
			matchField.put(fieldName, 0)
			break;
		case "CUS_TYPE":
			matchField.put(fieldName, "100")
			break;
		//case "EXCHANGE_RATE":
		//	matchField.put(fieldName, "1,00")
		//	break;
		default:
			matchField.put(fieldName, "")
			break;
	}
	return matchField;
}

def executeInsert(def map, def db, Logger logger){
	
	def result=""
	def sql = "INSERT INTO CIGATEWAY.TBL_TRANS_OUT_GTW" + "\n" +
			"(CHECK_CODE, CREATE_FILE_RESULT_FLAG, FILE_NAME_RESULT, TRX_TYPE, SD_TIME, SERIAL_NO, RELATION_NO, RESPONSE_CODE, O_CI_CODE, R_CI_CODE, O_INDIRECT_CODE, R_INDIRECT_CODE, FEE_CI_CODE, TRX_DATE, CURRENCY, EXCHANGE_RATE, AMOUNT, SD_CODE, SD_NAME, SD_ADDR, SD_ACCNT, SD_ID_NO, SD_ISSUE_DATE, SD_ISSUER, RV_CODE, RV_NAME, RV_ADDR,RV_ACCNT, RV_ID_NO, RV_ISSUE_DATE, RV_ISSUER, CONTENT, CUS_TYPE, AUTHORIZED, FEE_FLAG, REFERENCE, EX_E_SIGN, CREATE_TIME, APPR_ID, E_SIGN, OPTION1, OPTION2, OPTION3, SPARE, MAC, FILE_NAME, CONTENTFROMFILE, ERR_MSG, LINEPOSITION, FILEPROCESSINGTIME, CONTENT_EX) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	try{
		db.execute(sql, [map.CHECK_CODE,map.CREATE_FILE_RESULT_FLAG, map.FILE_NAME_RESULT, map.TRX_TYPE, map.SD_TIME, map.SERIAL_NO, map.RELATION_NO, map.RESPONSE_CODE, map.O_CI_CODE, map.R_CI_CODE, map.O_INDIRECT_CODE, map.R_INDIRECT_CODE, map.FEE_CI_CODE, map.TRX_DATE, map.CURRENCY, map.EXCHANGE_RATE, map.AMOUNT, map.SD_CODE, map.SD_NAME, map.SD_ADDR, map.SD_ACCNT, map.SD_ID_NO, map.SD_ISSUE_DATE, map.SD_ISSUER, map.RV_CODE, map.RV_NAME, map.RV_ADDR, map.RV_ACCNT, map.RV_ID_NO, map.RV_ISSUE_DATE, map.RV_ISSUER, map.CONTENT, map.CUS_TYPE, map.AUTHORIZED, map.FEE_FLAG, map.REFERENCE, map.EX_E_SIGN, map.CREATE_TIME, map.APPR_ID, map.E_SIGN, map.OPTION1, map.OPTION2, map.OPTION3, map.SPARE, map.MAC, map.FILE_NAME, map.CONTENFROMFILE, map.ERR_MSG, map.LINEPOSITION.toInteger(), map.FILEPROCESSINGTIME, map.CONTENT_EX]);
		result = "Successfully"
	}catch(Exception e){
		logger.error e.toString(); 
		sendMailNotify(-1, "ERROR insert database CITAD: ", e.toString())
		result = e.toString()
		db.close()
	}
	return result
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}