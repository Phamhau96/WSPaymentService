#!/usr/bin/env bash
curl \
--header "Content-Type: text/xml;charset=UTF-8" \
--header "SOAPAction:hello" \
--data \
"<soapenv:Envelope
    xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"
    xmlns:hel=\"HelloService.wsdl\">
   <soapenv:Header/>
   <soapenv:Body>
      <hel:dealInput>
         <firstName>Hu121y</firstName>
         <lastName>xxxxxxxxxHu121y</lastName>
      </hel:dealInput>
   </soapenv:Body>
</soapenv:Envelope>" \
http://127.0.0.1:8833/test/hello-service
