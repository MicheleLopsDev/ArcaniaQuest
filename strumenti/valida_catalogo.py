import json,io,sys
c=json.load(io.open('content/moduli/catalogo.json',encoding='utf-8'))
DIR={'nord':(0,-1),'sud':(0,1),'ovest':(-1,0),'est':(1,0)}
err=[];tot=0;visti={}
for fam in ['iniziali','corridoi','stanze']:
    for m in c[fam]:
        tot+=1; i=m['id']
        w,d=m['ingombro']['w'],m['ingombro']['d']; cel=m['caselle']
        if len(cel)!=d: err.append((i,'righe %d != profondita %d'%(len(cel),d)))
        for r in cel:
            if len(r)!=w: err.append((i,'riga larga %d != %d: %r'%(len(r),w,r)))
        for k in m['connettori']:
            x,z=k['cella']
            if not(0<=x<w and 0<=z<d): err.append((i,'connettore fuori %s'%k)); continue
            if cel[z][x]!='1': err.append((i,'connettore su roccia %s'%k)); continue
            # un connettore si apre dove di la' c'e' roccia o il fuori
            dx,dz=DIR[k['lato']]; nx,nz=x+dx,z+dz
            dentro = 0<=nx<w and 0<=nz<d and cel[nz][nx]=='1'
            if dentro: err.append((i,'connettore che da su una casella interna %s'%k))
        if 'partenza' in m:
            x,z=m['partenza']['cella']
            if cel[z][x]!='1': err.append((i,'partenza su roccia'))
        ch=(m['pesca']['tabella'],m['pesca']['valore'])
        if ch in visti: err.append((i,'stesso tiro di %s: %s'%(visti[ch],ch)))
        visti[ch]=i
d66=sorted(v for (t,v) in visti if t=='d66')
mancanti=[int(str(a)+str(b)) for a in range(1,7) for b in range(1,7) if int(str(a)+str(b)) not in d66]
print('moduli: %d  (iniziali %d, corridoi %d, stanze %d)'%(tot,len(c['iniziali']),len(c['corridoi']),len(c['stanze'])))
print('tavola d66: %d/36  mancanti: %s'%(len(d66),mancanti or 'nessuno'))
if err:
    print('\nERRORI:'); [print('  %s: %s'%e) for e in err]; sys.exit(1)
print('\ncatalogo valido')
