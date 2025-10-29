#!/usr/bin/env bash

# this converts PDF images to pngs for use in docs
# requires that you install imagemagick
# sudo yum -y install ImageMagick

function outputimage() {
    # first arg is filename, second arg is output name
    convert -density 600 $1 -quality 100 $2
}

function producepng() {
    # first arg is pdf filename
    filename=$(basename -- "$1")
    extension="${filename##*.}"
    filename="${filename%.*}"
    echo "Converting $1 to $filename.png"
    outputimage $1 "$filename.png"
}

producepng $1
