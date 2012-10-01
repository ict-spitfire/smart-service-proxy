package eu.spitfire_project.smart_service_proxy.core;

//import com.hp.hpl.jena.rdf.model.Model;
//import com.hp.hpl.jena.rdf.model.ModelFactory;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import eu.spitfire_project.smart_service_proxy.utils.Cell;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Assumptions:
 * - sizeof(table_id_t) = 1
 * - sizeof(command_t) = 1
 * - table_size <= 255
 */
public class ShdtSerializer {

    private static Logger log = Logger.getLogger(ShdtSerializer.class.getName());

	private Map<Byte, String> lookup_table;
	private int TABLE_SIZE;
	private static final byte nidx = (byte)0xff;
	private static final byte TUPLE_SIZE = 3;

	//enum Commands { CMD_INSERT = 0, CMD_END = 0xff };
	private static final byte CMD_INSERT = (byte)0xfe;
	private static final byte CMD_END = (byte)0xff;

	public ShdtSerializer(int table_size) {
		this.TABLE_SIZE = table_size;
		this.lookup_table = new HashMap<Byte, String>();
	}

	public void reset() {
		lookup_table.clear();
	}

	public int fill_buffer(byte[] buffer, StmtIterator iter) {
		Cell<Integer> buffer_pos = new Cell<Integer>(0);

		byte[] ids = new byte[3];
		try {
			while(true) {
				Statement st = iter.nextStatement();
				boolean ins = insert_statement(buffer, buffer_pos, ids, st);
				if(!ins) { break; }

				final int cmdlen = TUPLE_SIZE * 1;
				if(buffer.length - buffer_pos.get() < cmdlen) { break; }

				for(int p=0; p<TUPLE_SIZE; p++) {
					buffer[buffer_pos.get()] = ids[p];
					buffer_pos.set(buffer_pos.get() + 1);
				}
			}
		}
		catch(NoSuchElementException e) {
		}
		return buffer_pos.get();
	}

	private boolean insert_statement(byte[] buffer, Cell<Integer> buffer_pos, byte[] ids, Statement st) {
		ids[0] = insert_hash_avoid(st.getSubject().toString(), nidx, nidx, buffer, buffer_pos);
		if(ids[0] == nidx) { return false; }
		ids[1] = insert_hash_avoid(st.getPredicate().getLocalName(), ids[0], nidx, buffer, buffer_pos);
		if(ids[1] == nidx) { return false; }
		ids[2] = insert_hash_avoid(st.getObject().toString(), ids[1], ids[2], buffer, buffer_pos);
		if(ids[2] == nidx) { return false; }

		return true;
	}

	byte hash(String s) {
		byte r = 0;
		for(int i=0; i<s.length() /*&& i<12*/; i++) {
			r = (byte) (((5*r) + s.getBytes()[i]) % TABLE_SIZE);
		}
		return (byte) (r % TABLE_SIZE);
	}



	private byte insert_hash_avoid(String data, byte avoid1, byte avoid2, byte[] buffer, Cell<Integer> buffer_pos) {
		byte id = hash(data);
		byte id2 = nidx;
		for(int offs = 0; ; offs++) {
			id2 = (byte) ((id + offs) % TABLE_SIZE);
			if(id2 == avoid1 || id2 == avoid2) { continue; }
			String t = lookup_table.get(id2);
			if(t == null) {
				if(insert(id2, data, buffer, buffer_pos) != 0) { return nidx; }
				break;
			}

			else if(data.equals(t)) {
				break;
			}

			else {
				lookup_table.remove(id2);
				if(insert(id2, data, buffer, buffer_pos) != 0) { return nidx; }
				break;
			}
		} // for offs

		return id2;
	}

	private int insert(byte id, String data, byte[] buffer, Cell<Integer> buffer_pos) {
		data += "\0";
		int l = data.length();
		int cmdlen = 1 + 1 + 1 + l;
		if(buffer.length - buffer_pos.get() < cmdlen) { return 1; }

		buffer[buffer_pos.get()] = nidx; buffer_pos.set(buffer_pos.get() + 1);
		buffer[buffer_pos.get()] = CMD_INSERT; buffer_pos.set(buffer_pos.get() + 1);
		buffer[buffer_pos.get()] = id; buffer_pos.set(buffer_pos.get() + 1);

		for(int i=0; i<l; i++) {
			buffer[buffer_pos.get()] = (byte)data.charAt(i);
			buffer_pos.set(buffer_pos.get() + 1);
		}
		//buffer_pos += l;
		lookup_table.put(id, data);
		return 0;
	}

	public void read_buffer(Model model, byte[] buffer) {
		int i = 0;
		while(i < buffer.length) {
			log.debug("SHDT: i=" + i + " buflen=" + buffer.length);
				
			byte tid = buffer[i]; i++;
			
			log.debug("SHDT: tid=" + tid);
			
			if(tid == nidx) { // command mode
				/*if(buffer.length - i < 1) {
					// reading of buffer done successfully
					return;
				}*/
				if(i >= buffer.length) { break; }

				byte cmd = buffer[i]; i++;
				log.debug("SHDT: cmd=" + cmd);
				
				if(cmd == CMD_INSERT) {
					byte pos = buffer[i]; i++;

					// i >= buffer.length should not happen at this point if the
					// input is legitimate
					if(i >= buffer.length) {
						log.debug("SHDT: buffer ended with CMD_INSERT, ignored!");
						break;
					}

					String s = new String(buffer, i, buffer.length - i);
					s = s.substring(0, s.indexOf('\0'));
					lookup_table.put(pos, s);
					i += s.length() + 1;
					log.debug("SHDT: inserted \"" + s + "\" at " + pos);
				}
				else { // if(cmd == CMD_END) {
					//log.debug("SHDT: CMD_END i-- @", i);
					i--;
					continue;
					//reading of buffer done successfully
					//return;
				}
			}

			else { // tuple mode
				if(i + 1 >= buffer.length) {
					log.debug("SHDT: buffer ended with incomplete tuple command, ignored!");
					break;
				}
				byte sid = tid, pid = buffer[i], oid = buffer[i + 1];
				
				log.debug("SHDT: tuple: (" + sid + " " + pid + " " + oid + ")");

				String s = lookup_table.get(sid);
				String p = lookup_table.get(pid);
				String o = lookup_table.get(oid);
				i += 2;
				log.debug("SHDT: inserting: (" + s + ", " + p + ", " + o + ")");

				boolean isLiteral = o.startsWith("\"");
				model.add(model.createStatement(
						model.createResource(s.substring(1, s.length() - 1)),
						model.createProperty(p.substring(1, p.length() - 1)),
						isLiteral ? model.createLiteral(o.substring(1, o.length() - 1)) : model.createResource(o.substring(1, o.length() - 1))
				));

			}
		}
	}
}
