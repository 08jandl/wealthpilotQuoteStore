#!/bin/sh
jq -r '.categories[] | select (.parentId == 426) | [.id, .name] | @csv' categories_all.json