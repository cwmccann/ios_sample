#!/usr/bin/env bash

red=$(tput setaf 1)
green=$(tput setaf 2)
color_reset=$(tput sgr0)

error_echo() {
  printf "\\n${red}%s${color_reset}\\n" "$1"
}

info_echo() {
  printf "\\n${green}%s${color_reset}\\n" "$1"
}
