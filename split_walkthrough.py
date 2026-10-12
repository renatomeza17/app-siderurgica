from pathlib import Path
import re

root=Path.cwd()
source=root/'outputs/guion_walkthrough_sprints_1_2_3.md'
text=source.read_text(encoding='utf-8')
matches=list(re.finditer(r'^## Sprint .*?HU(\d+):.*$',text,re.M))
assert len(matches)==9
opening=text[text.index('### Apertura'):matches[0].start()].strip()
closing_start=text.index('## Cierre de la exposición')
notes_start=text.index('### Notas para conducir la demo')
closing=text[closing_start:notes_start].strip()
notes=text[notes_start:].strip()
sections={int(m.group(1)):text[m.start():(matches[i+1].start() if i+1<len(matches) else closing_start)].strip() for i,m in enumerate(matches)}
owners={1:1,2:1,3:2,4:1,5:2,6:1,7:1,8:2,9:2}
passes={
2:'Con la sesión y los permisos explicados, le paso la palabra a mi compañero para mostrar cómo conservamos información y preparamos operaciones cuando se pierde la conexión.',
3:'Ya vimos qué se guarda localmente y qué queda pendiente de envío. Ahora mi compañero seguirá una cotización desde el formulario hasta su respuesta en el ERP.',
4:'Con la cotización registrada y su conversión recorrida, mi compañero continuará con los estados del pedido y la generación de la venta.',
5:'La venta ya nos devuelve sus datos e importes. Le paso la palabra a mi compañero para mostrar cómo usamos ese resultado para generar y compartir su constancia digital.',
7:'Acabamos de recorrer el stock y sus alertas. Mi compañero continuará con la reposición: proveedor, orden de compra y recepción; después mostrará cómo capturamos y revisamos los documentos.'
}
def spoken(s):
    s=re.sub(r'```.*?```','',s,flags=re.S)
    s=re.sub(r'^\*En pantalla:.*$','',s,flags=re.M)
    s=re.sub(r'^#{1,6}.*$','',s,flags=re.M)
    return len(s.split())
counts={1:spoken(opening),2:spoken(closing)}
for n,s in sections.items(): counts[owners[n]]+=spoken(s)+len(passes.get(n,'').split())
total=sum(counts.values())
rows=[('| Apertura | Persona 1 | Presentación del recorrido y alcance |')]
for n,s in sections.items():
    title=s.splitlines()[0].split(': ',1)[1]
    rows.append(f'| HU{n:02d} · Sprint {(n-1)//3+1} | Persona {owners[n]} | {title} |')
rows.append('| Cierre | Persona 2 | Avance global, pendientes y paso a la demo |')
guide=f'''# Guion para dos expositores — Sprints 1, 2 y 3

El reparto equilibra el discurso y la carga de código, no el número de historias. Persona 1 tiene aproximadamente {counts[1]} palabras habladas ({counts[1]/total:.0%}) y Persona 2, {counts[2]} ({counts[2]/total:.0%}); no se cuentan bloques de código ni referencias de pantalla. El tiempo real depende de la demo y las pausas.

Como referencia para una sesión de 40 minutos, reservar unos 20 minutos por persona, incluyendo explicación del código. No es necesario leer cada línea: leer la narración y señalar el fragmento correspondiente. Los nombres «Persona 1» y «Persona 2» pueden sustituirse por los de los integrantes.

| Orden | Expositor | Contenido |
|---|---|---|
{chr(10).join(rows)}

Se conserva íntegro el contenido técnico del guion original, sus fragmentos y advertencias de alcance. Los pases de palabra se añaden después de completar una historia. Cada sección la presenta una sola persona, incluida su navegación entre pantalla, lógica, repositorio y persistencia.

Quien expone controla la navegación del código o indica al compañero qué fragmento mostrar. Antes de la presentación, ensayar especialmente los cambios HU02→HU03, HU03→HU04, HU04→HU05, HU05→HU06 y HU07→HU08. El segundo expositor escucha los pasos anteriores: las historias reutilizan piezas ya explicadas.
'''
master=[guide,'## Persona 1 — Apertura\n\n'+opening]
personal={1:[guide,'## Tu intervención inicial\n\n'+opening],2:[guide,'## Tu primera intervención\n\nEntras después de HU02. Continúa desde la sesión y los permisos ya explicados.']}
for n,s in sections.items():
    content=s.replace('## Sprint',f'## Persona {owners[n]} · Sprint',1)
    master.append(content)
    personal[owners[n]].append(content)
    if n in passes:
        transfer=f'**Persona {owners[n]} — pase de palabra:**\n\n{passes[n]}'
        master.append(transfer)
        personal[owners[n]].append(transfer)
    if n<9 and owners[n]!=owners[n+1]:
        personal[owners[n+1]].append(f'*Recibes la palabra después de HU{n:02d}. Continúa con el recorrido siguiente.*')
master.extend(['## Persona 2 — Cierre\n\n'+closing,notes])
personal[2].append('## Tu cierre\n\n'+closing)
for n in (1,2): personal[n].append(notes)
outdir=root/'outputs'
outputs={
'guion_dos_expositores_sprints_1_2_3.md':master,
'guion_persona_1_sprints_1_2_3.md':personal[1],
'guion_persona_2_sprints_1_2_3.md':personal[2]
}
for name,parts in outputs.items():
    final='\n\n'.join(parts)+'\n'
    (outdir/name).write_text(final,encoding='utf-8')
    print(outdir/name)
master_text=(outdir/'guion_dos_expositores_sprints_1_2_3.md').read_text(encoding='utf-8')
original_blocks=re.findall(r'```.*?```',text,re.S)
assert re.findall(r'```.*?```',master_text,re.S)==original_blocks
for n,s in sections.items():
    rendered=s.replace('## Sprint',f'## Persona {owners[n]} · Sprint',1)
    assert rendered in master_text
    assert rendered in (outdir/f'guion_persona_{owners[n]}_sprints_1_2_3.md').read_text(encoding='utf-8')
print(f'Reparto hablado: {counts}; {len(original_blocks)} bloques preservados en orden.')
