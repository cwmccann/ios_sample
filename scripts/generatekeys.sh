#!/usr/bin/env bash

##
# Generates a new key and signing request.  This should only be done once, or when the certificate expires.
# Go to https://developer.apple.com/account/ios/certificate/ and upload the build/CertificateSigningRequest.certSigningRequest to it.
# Then download the file and put it in src/test/resources/codesign
##

#Change to the directory where the script resides so that the relitive paths below will work
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd ${scriptDir}

source "${scriptDir}/functions.sh"

#path where the generated keys will live
keyPath=$(realpath ../src/test/resources/codesign/miw_dev.key)
keyDir=$(dirname ${keyPath})

#Check if the key already exists
if test -f ${keyPath}
then
    info_echo "Key already exists are you sure you want to generate a new one? (Y/N)"
    read -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]
    then
        info_echo "Abort"
        [[ "$0" = "$BASH_SOURCE" ]] && exit 1 || return 1 # handle exits from shell or function but don't exit interactive shell
    fi
fi

info_echo "Generating the key to ${keyPath}"
mkdir -p ${keyDir}
openssl genrsa -out "${keyPath}" 2048
info_echo "Done"

info_echo "Generating signing request:"
certRequestFile=$(realpath "../build/CertificateSigningRequest.certSigningRequest")
mkdir -p ../build
if test -f ${certRequestFile}
then
    rm ${certRequestFile}
fi
openssl req -new -key "${keyPath}" -out ${certRequestFile} -subj "/emailAddress=chrism@mobileintegration-group.com, CN=MIW Dev Key, C=CA"
info_echo "Done"

info_echo "Upload ${certRequestFile} to the apple developer portal and download the resulting file to ${keyPath}/ios_development.cer"
open https://developer.apple.com/account/ios/certificate/




