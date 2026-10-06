import subprocess,time,re,xml.etree.ElementTree as ET
PACKAGE='com.qingsongji.diary'
def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def tree():
 for attempt in range(6):
  try:
   adb('shell','rm','-f','/sdcard/diary-ui.xml')
   adb('shell','uiautomator','dump','/sdcard/diary-ui.xml')
   return ET.fromstring(adb('shell','cat','/sdcard/diary-ui.xml'))
  except (subprocess.CalledProcessError,ET.ParseError):
   time.sleep(2)
 raise AssertionError('Android accessibility tree did not become available')
def find(label):
 for attempt in range(12):
  root=tree()
  for node in root.iter('node'):
   text=node.get('text','')+' '+node.get('content-desc','')
   if label in text:
    nums=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(nums)==4 and nums[2]>nums[0] and nums[3]>nums[1]:return nums
  width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1])
  down=3<=attempt<9
  start,end=(0.3,0.78) if down else (0.78,0.3)
  adb('shell','input','swipe',str(width//2),str(int(height*start)),str(width//2),str(int(height*end)),'350')
  time.sleep(1)
 print(ET.tostring(root,encoding='unicode'))
 raise AssertionError('UI label not found: '+label)
def tap(label):
 a,b,c,d=find(label);adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(2)
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(6)
find('记一次小便');tap('记一次小便');find('保存记录');tap('保存记录');find('小便')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(5)
find('小便');find('1')
tap('我的');find('安卓 APP');find('数据与备份')
sections=[node for node in tree().iter('node') if any(child.get('text')=='数据与备份' for child in node)]
assert any({'1','条记录'} <= {child.get('text','').strip() for child in node} for node in sections), 'Saved record count was not retained'
tap('从备份恢复');time.sleep(2)
active=[line for line in adb('shell','dumpsys','activity','activities').splitlines() if 'topResumedActivity=' in line or 'mResumedActivity:' in line]
assert any('documentsui' in line for line in active), 'System file picker did not open: '+str(active)
adb('shell','input','keyevent','4');time.sleep(2);find('安卓 APP')
tap('扫码下载安卓 APP');find('下载安卓 APP');adb('shell','input','keyevent','4');time.sleep(2)
print('Offline Android UI, save/relaunch, native picker cancellation and QR view checks passed.')
