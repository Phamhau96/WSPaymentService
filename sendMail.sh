#!/bin/bash
###Mail parameter####
recipients="haupv2@vpbank.com.vn"
subject="[FFC_KONDOR] THONG BAO GUI DIEN PAYMENT"
from="kplus@ho.vpb.com.vn"
message_txt=""

message_txt=$2+$3
/usr/sbin/sendmail "$recipients" << EOF
subject:$subject $1
from:$from
$message_txt  
EOF
echo "Send mail successfully"

#### Send Mail #####
#if [ $2 -eq 0 ]
#then
#message_txt="[ERROR] Loi ghi file\n"+$3
#/usr/sbin/sendmail "$recipients" << EOF
#subject:$subject $1
#from:$from
#$message_txt  
#EOF
#echo "Send mail successfully"
#
#fi
#if [ $2 -eq 1 ]  
#then
#message_txt="[ERROR] Loi sai format dien"+$3
#/usr/sbin/sendmail "$recipients" << EOF
#subject:$subject $1
#from:$from
#$message_txt  
#EOF
#echo "Send mail successfully"
#
#fi
#if [ $2 -eq -1 ]
#then
#message_txt="[ERROR] Loi database:\n"+$3 
#/usr/sbin/sendmail "$recipients" << EOF
#subject:$subject $1
#from:$from
#$message_txt  
#EOF
#echo "Send mail successfully"
#fi
