# -*- coding: utf-8 -*-
"""
@author: chineseocr
"""
import web
web.config.debug  = False
import uuid
import json
import os
import sys
import time
import cv2
import numpy as np
from helper.image import read_url_img,base64_to_PIL,get_now
from PIL import Image
from dnn.text import detect_lines
from config import scale,maxScale,TEXT_LINE_SCORE
import json
render = web.template.render('templates', base='base')

billList =[]
root = './test/'
timeOutTime=5

def job(imgPath):
    img = Image.open(imgPath)
    if img is not None:
        image = np.array(img)
        image =  cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
        boxes,scores = detect_lines(image,scale=scale,maxScale=maxScale)
        data =[]
        n = len(boxes)
        for i in range(n):
            box = boxes[i]
            box =  [int(x) for x in box]
            if scores[i]>TEXT_LINE_SCORE:
               data.append({'box':box,'prob':round(float(scores[i]),2),'text':None})
        
        res = {'data':data,'errCode':0}
    else:
        res = {'data':[],'errCode':3}
    return res

result=job(sys.argv[1])
print(json.dumps(result))

