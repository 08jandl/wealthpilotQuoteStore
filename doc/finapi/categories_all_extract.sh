#!/bin/sh
jq -r '.categories[].name' categories_all.json | sort
