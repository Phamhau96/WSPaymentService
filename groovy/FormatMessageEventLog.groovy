import com.misys.tools.integration.api.message.GMessage
import com.misys.tools.integration.utils.ResultTable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import groovy.transform.Field

GMessage message = message


final Logger logger = LoggerFactory.getLogger("FormatMessageEventLog")

ResultTable resultTable = new ResultTable(
        ['EventTime',
         'EventType',
         'Source',
         'MessageId',
         'Sequence',
         'Component',
         'Status',
         'FlowEnd',
         'FileName',
         'FileTimestamp'
        ], null)

resultTable.addRow([message.properties['MessageEventTimestamp'],
                    message.properties['MessageEventType'],
                    message.properties['MessageEventAttribute.source'],
                    message.properties['MessageEventAttribute.messageId'],
                    message.properties['MessageEventAttribute.sequence'],
                    message.properties['MessageEventComponent'],
                    message.properties['MessageEventAttribute.status'],
                    message.properties['MessageEventAttribute.flowEnd'],
                    message.properties['MessageEventProperty.fileName'],
                    message.properties['MessageEventProperty.fileTimeStamp']])

return message
        .result()
        .messageBody(resultTable)
        .build()
