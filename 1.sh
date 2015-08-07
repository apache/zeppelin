#/bin/bash
nohup mvn package -DskipTests -Phadoop-2.3 -Ppyspark -B 2>&1 > install.log &
count=0
nextPrint=$(date +%s)
while read line; do
   count=$((count+=1))
   now=$(date +%s)  
   if [[ $now -gt $nextPrint ]];then
  	 echo "Text read from file: $count"
         sleep 2
         now=$(date +%s)
         nextPrint=$((now+10)) 
   fi
         
done < "install.log"
