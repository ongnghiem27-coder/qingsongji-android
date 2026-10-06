import subprocess,time,re,xml.etree.ElementTree as ET
PACKAGE='com.qingsongji.diary'
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def tree():
 adb('shell','uiautomator','dump','/sdcard/diary-ui.xml')
 return ET.fromstring(adb('shell','cat','/sdcard/diary-ui.xml'))
def find(label):
 for attempt in range(10):
  root=tree()
  for node in root.iter('node'):
   text=node.get('text','')+' '+node.get('content-desc','')
   if label in text:
    nums=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(nums)==4 and nums[2]>nums[0] and nums[3]>nums[1]:return nums
  time.sleep(2)
 raise AssertionError('UI label not found: '+label)
def tap(label):
 a,b,c,d=find(label);adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(2)
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(6)
find('记一次小便');tap('记一次小便');find('保存记录');tap('保存记录');find('小便')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(5)
find('小便');find('1')
tap('我的');find('安卓 APP');tap('从备份恢复');time.sleep(2)
assert PACKAGE not in adb('shell','dumpsys','activity','activities').split('mResumedActivity')[-1].splitlines()[0], 'File picker did not open'
adb('shell','input','keyevent','4');time.sleep(2);find('安卓 APP')
tap('扫码下载安卓 APP');find('下载安卓 APP');adb('shell','input','keyevent','4');time.sleep(2)
print('Offline Android UI, save/relaunch, native picker cancellation and QR view checks passed.')
