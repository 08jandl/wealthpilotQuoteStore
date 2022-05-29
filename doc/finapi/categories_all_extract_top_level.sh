#!/bin/sh
jq -r '.categories[] | select(.children != null) | [.id, .name] | @csv' categories_all.json