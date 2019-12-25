import mxnet as mx
import sys
import json
from cnocr import CnOcr

class NumpyEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, (np.int_, np.intc, np.intp, np.int8,
            np.int16, np.int32, np.int64, np.uint8,
            np.uint16, np.uint32, np.uint64)):
            return int(obj)
        elif isinstance(obj, (np.float_, np.float16, np.float32,
            np.float64)):
            return float(obj)
        elif isinstance(obj,(np.ndarray,)): #### This is the fix
            return obj.tolist()
        return json.JSONEncoder.default(self, obj)
		

ocr = CnOcr()
img = mx.image.imread(str(sys.argv[1]), 1)
res = ocr.ocr(img)
jsonStr=json.dumps(res, cls=NumpyEncoder)
print(jsonStr)

