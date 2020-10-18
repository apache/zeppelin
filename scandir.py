import os
a = os.listdir(".")
def scanpath(x):
    entries = os.listdir(x)
    resultcnt = 0
    resultbytes = 0
    for entry in entries:
        xpath = os.path.join(x,entry)
        if os.path.isfile(xpath):
            resultbytes = resultbytes + os.path.getsize(xpath)
            resultcnt = resultcnt + 1
        elif os.path.isdir(xpath):
            (currentcnt,currentbytes) = scanpath(xpath)
            resultcnt = resultcnt + currentcnt
            resultbytes = resultbytes + currentbytes
    return (resultcnt,resultbytes)
unsortedlist = []
for an_entry in a:
    thepath = os.path.join(".",an_entry)
    if os.path.isfile(thepath):
        continue
    (currentcnt,currentbytes) = scanpath(thepath)
    unsortedlist.append((currentcnt,currentbytes,thepath))
unsortedlist.sort(key= lambda tup: -tup[0])
for listentry in unsortedlist:
    (currentcnt,currentbytes,thepath) = listentry
    print '%-10s %-10s %s' % (currentcnt,currentbytes,thepath)
