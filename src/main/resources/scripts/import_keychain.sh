#!/usr/bin/env bash


# Usage info
show_help() {
cat << EOF
Usage: ${0##*/} [-hd] [-f KEYCHAINPATH] [-p PASSWORD] [KEY]...
Create a keychain and import the supplied keys to it.

   -h           Display this help and exit
   -d           delete the keychain if it exists
   -p password  The password for the keychain.  You can also use \$KEYPASS variable
   -f keychain  The path to the keychain to create
EOF
}

delete_keychain() {
    echo "Deleting keychain $1"
    rm "$1-db"
}

# Initialize our own variables:
keychain=""
delete_keychain=false
keychain_pass=${KEYPASS:-""}


#check the variables are defined
#[[ -z "${keychain}" ]] && { echo "Error: \$keychain not found"; exit 1; }
#[[ -z "${keychainPassword}" ]] && { echo "Error: \$keychainPassword not found"; exit 1; }
#[[ -z "${keys}" ]] && { echo "Error: \$keys to import not found.  USe a ; delimited list"; exit 1; }
#
#
#if test ! -f "${keychain}-db"
#then
#  echo "Creating new keychain"
#  security create-keychain -p "${keychainPassword}" $keychain
#fi
#
#
#echo "Importing Keys to keychain"
#security import $HOME/projects/ios-sample/src/test/resources/codesign/miw_dev.key -k ${keychain} -P "${keychainPassword}" -T /usr/bin/codesign -T /usr/bin/security
#security import $HOME/projects/ios-sample/src/test/resources/codesign/ios_development.cer -k ${keychain} -P "${keychainPassword}" -T /usr/bin/codesign -T /usr/bin/security
#security set-key-partition-list -S apple-tool:,apple: -k "${keychainPassword}" "${keychain}"

while getopts ":dhp:f:" opt
do
  case ${opt} in
    h)
      show_help
      exit 0
      ;;
    d)
      delete_keychain=true
      ;;
    f)
      keychain=$OPTARG
      ;;
    p)
      keychain_pass=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit 1
      ;;
    :)
      echo "Option -$OPTARG requires an argument." >&2
      exit 1
      ;;
  esac
done
shift "$((OPTIND-1))"   # Discard the options and sentinel --

# Everything that's left in "$@" is a non-option.  In our case, a FILE to process.
#printf 'keychain=<%s>\nkeychain_pass=<%s>\ndelete keychain=<%s>\nLeftovers:\n' ${keychain} ${keychain_pass} ${delete_keychain}
#printf '<%s>\n' "$@"

#make sure we have the required parameters
[[ -z "${keychain}" ]] && { echo "Error: keychain is required"; exit 1; }
[[ -z "${keychain_pass}" ]] && { echo "Error: keychain password is required"; exit 1; }
[[ -z "$1" ]] && { echo "Error: at least one key is required"; exit 1; }

#delete the keychain if we need to
if [ "$delete_keychain" = true ] && [ -f "${keychain}-db" ]
then
    delete_keychain ${keychain}
fi

#create the keychain
if test ! -f "${keychain}-db"
then
  echo "Creating new keychain ${keychain}"
  security create-keychain -p "${keychain_pass}" ${keychain}
fi

#unlock the keychain and make it available for an hour
security -v list-keychains -s "${keychain}-db"
security -v unlock-keychain -p "${keychain_pass}" "${keychain}-db"
security set-keychain-settings -t 3600 "${keychain}-db"

for key in "$@"; do
    echo "Importing Key ${key} to keychain "
    security import ${key} -k ${keychain} -P "${keychain_pass}" -T /usr/bin/codesign -T /usr/bin/security
done

echo "setting partition list to allow codesign to access keychain and adding the keychain to the path"
security set-key-partition-list -S apple-tool:,apple: -k "${keychain_pass}" "${keychain}" > /dev/null 2>&1

exit 0

