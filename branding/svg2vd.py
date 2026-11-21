import re, sys, xml.etree.ElementTree as ET
NS='{http://www.w3.org/2000/svg}'
def color(c, op=1.0):
    c=c.lstrip('#'); a=int(round(op*255)); return '#%02X%s'%(a,c.upper())
def parse_defs(defs):
    g={}
    for el in defs:
        tag=el.tag.replace(NS,'')
        stops=[(float(s.get('offset')), color(s.get('stop-color'), float(s.get('stop-opacity','1')))) for s in el]
        if tag=='radialGradient':
            g[el.get('id')]=dict(type='radial',cx=el.get('cx'),cy=el.get('cy'),r=el.get('r'),stops=stops)
        else:
            g[el.get('id')]=dict(type='linear',x1=el.get('x1'),y1=el.get('y1'),x2=el.get('x2'),y2=el.get('y2'),stops=stops)
    return g
def grad_xml(attr, gr, ind='        '):
    items=''.join(f'{ind}        <item android:offset="{o}" android:color="{c}"/>\n' for o,c in gr['stops'])
    if gr['type']=='radial':
        head=f'android:type="radial" android:centerX="{gr["cx"]}" android:centerY="{gr["cy"]}" android:gradientRadius="{gr["r"]}"'
    else:
        head=f'android:type="linear" android:startX="{gr["x1"]}" android:startY="{gr["y1"]}" android:endX="{gr["x2"]}" android:endY="{gr["y2"]}"'
    return f'{ind}<aapt:attr name="android:{attr}">\n{ind}    <gradient {head}>\n{items}{ind}    </gradient>\n{ind}</aapt:attr>\n'
def shape_path(el):
    tag=el.tag.replace(NS,'')
    if tag=='path': return el.get('d')
    if tag=='rect':
        w,h=el.get('width'),el.get('height'); return f'M0,0h{w}v{h}h-{w}z'
    if tag in('circle','ellipse'):
        cx=float(el.get('cx'));cy=float(el.get('cy'))
        rx=float(el.get('r') or el.get('rx')); ry=float(el.get('r') or el.get('ry'))
        return f'M{cx-rx},{cy}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0z'
def convert(svg_path, out):
    root=ET.parse(svg_path).getroot()
    defs=root.find(NS+'defs'); g=parse_defs(defs) if defs is not None else {}
    body=''
    for el in root:
        if el.tag==NS+'defs': continue
        d=shape_path(el); attrs=[f'android:pathData="{d}"']; inner=''
        fill=el.get('fill','#000000'); stroke=el.get('stroke')
        if fill.startswith('url('): inner+=grad_xml('fillColor', g[fill[5:-1]])
        elif fill!='none': attrs.append(f'android:fillColor="{color(fill, float(el.get("fill-opacity","1")))}"')
        if stroke:
            attrs.append(f'android:strokeWidth="{el.get("stroke-width","1")}"')
            if stroke.startswith('url('): inner+=grad_xml('strokeColor', g[stroke[5:-1]])
            else: attrs.append(f'android:strokeColor="{color(stroke, float(el.get("stroke-opacity","1")))}"')
        a='\n        '.join(attrs)
        body+=f'    <path\n        {a}' + (f'>\n{inner}    </path>\n' if inner else '/>\n')
    xml=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from logo SVG (tools/svg2vd.py). keepsake AI aurora capsule. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}</vector>
'''
    open(out,'w').write(xml)
convert(sys.argv[1], sys.argv[2])
